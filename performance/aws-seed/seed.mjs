import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { Agent } from 'node:https';
import { Sha256 } from '@aws-crypto/sha256-js';
import { STSClient, AssumeRoleCommand } from '@aws-sdk/client-sts';
import { fromIni } from '@aws-sdk/credential-providers';
import { NodeHttpHandler } from '@smithy/node-http-handler';
import { HttpRequest } from '@smithy/protocol-http';
import { SignatureV4 } from '@smithy/signature-v4';
import { faker } from '@faker-js/faker';
import { COMMANDS_PER_SECOND, CUSTOMER_COUNT, createCustomer, createRunId, parseConfig, scheduleAtMostFourCommandsPerSecond } from './lib.mjs';

function argumentsByName(argumentsList) {
  const result = new Map();
  for (let index = 0; index < argumentsList.length; index += 2) {
    result.set(argumentsList[index], argumentsList[index + 1]);
  }
  return result;
}

function resourceUrl(endpoint, resourcePath) {
  const basePath = endpoint.pathname.replace(/\/$/, '');
  return new URL(`${basePath}${resourcePath}`, endpoint);
}

async function readResponseBody(body) {
  const chunks = [];
  for await (const chunk of body) {
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function createInvoker(config) {
  const sourceCredentials = fromIni({ profile: config.profile });
  const sts = new STSClient({ region: config.region, credentials: sourceCredentials });
  const assumed = await sts.send(new AssumeRoleCommand({
    RoleArn: config.roleArn,
    RoleSessionName: `flash-booking-seed-${createRunId()}-${randomUUID().slice(0, 8)}`,
    DurationSeconds: 900,
  }));
  if (!assumed.Credentials?.AccessKeyId || !assumed.Credentials.SecretAccessKey || !assumed.Credentials.SessionToken) {
    throw new Error('STS did not return complete temporary credentials');
  }
  const signer = new SignatureV4({
    credentials: {
      accessKeyId: assumed.Credentials.AccessKeyId,
      secretAccessKey: assumed.Credentials.SecretAccessKey,
      sessionToken: assumed.Credentials.SessionToken,
    },
    region: config.region,
    service: 'execute-api',
    sha256: Sha256,
  });
  const httpHandler = new NodeHttpHandler({
    httpsAgent: new Agent({ family: 4 }),
    requestTimeout: 15_000,
  });
  return async (method, resourcePath, body, idempotencyKey) => {
    const url = resourceUrl(config.endpoint, resourcePath);
    const serializedBody = body === undefined ? undefined : JSON.stringify(body);
    const headers = { host: url.host };
    if (serializedBody) {
      headers['content-type'] = 'application/json';
    }
    if (idempotencyKey) {
      headers['idempotency-key'] = idempotencyKey;
    }
    const signed = await signer.sign(new HttpRequest({
      protocol: url.protocol,
      hostname: url.hostname,
      method,
      path: `${url.pathname}${url.search}`,
      headers,
      body: serializedBody,
    }));
    const { response } = await httpHandler.handle(signed);
    const text = await readResponseBody(response.body);
    let payload;
    if (text) {
      try { payload = JSON.parse(text); } catch { payload = undefined; }
    }
    return { status: response.statusCode, payload, errorType: response.headers['x-amzn-errortype'] };
  };
}

function requireStatus(response, expectedStatus, operation) {
  if (response.status !== expectedStatus) {
    const reason = response.payload?.message || response.payload?.title || 'no API error message';
    throw new Error(`${operation} returned HTTP ${response.status}, expected ${expectedStatus}: ${reason} (${response.errorType || 'no AWS error type'})`);
  }
  return response.payload;
}

async function runEndpointProof(invoke, runId) {
  const event = requireStatus(await invoke('POST', '/events', { name: `AWS endpoint proof ${runId}`, capacity: 2 }, `seed-${runId}-proof-event`), 201, 'POST /events');
  requireStatus(await invoke('GET', `/events/${event.id}`), 200, 'GET /events/{id}');
  const reservation = requireStatus(await invoke('POST', `/events/${event.id}/reservations`, { quantity: 1, customer: createCustomer(runId, 0) }, `seed-${runId}-proof-reservation`), 201, 'POST /events/{id}/reservations');
  requireStatus(await invoke('GET', `/reservations/${reservation.id}`), 200, 'GET /reservations/{id}');
  requireStatus(await invoke('DELETE', `/reservations/${reservation.id}`, undefined, `seed-${runId}-proof-cancel`), 200, 'DELETE /reservations/{id}');
  return { postEvent: 201, getEvent: 200, postReservation: 201, getReservation: 200, deleteReservation: 200 };
}

async function main() {
  const config = parseConfig(argumentsByName(process.argv.slice(2)));
  const runId = `${createRunId()}-${randomUUID().slice(0, 8)}`;
  faker.seed(Array.from(runId).reduce((total, character) => total + character.charCodeAt(0), 0));
  const invoke = await createInvoker(config);
  const startedAt = new Date();
  const endpoints = await runEndpointProof(invoke, runId);
  const event = requireStatus(await invoke('POST', '/events', { name: `AWS Faker seed ${runId}`, capacity: CUSTOMER_COUNT }, `seed-${runId}-event`), 201, 'seed POST /events');
  let createdCustomers = 0;
  await scheduleAtMostFourCommandsPerSecond(CUSTOMER_COUNT, async (index) => {
    const response = await invoke('POST', `/events/${event.id}/reservations`, { quantity: 1, customer: createCustomer(runId, index) }, `seed-${runId}-reservation-${index}`);
    requireStatus(response, 201, `seed reservation ${index}`);
    createdCustomers += 1;
  });
  const report = {
    runId,
    startedAt: startedAt.toISOString(),
    completedAt: new Date().toISOString(),
    seedEventId: event.id,
    endpoints,
    createdCustomers,
    createdReservations: createdCustomers,
    commandRateLimitPerSecond: COMMANDS_PER_SECOND,
    notificationConsumerExpected: false,
  };
  const directory = path.join(path.dirname(fileURLToPath(import.meta.url)), 'results');
  await mkdir(directory, { recursive: true });
  await writeFile(path.join(directory, `${runId}.json`), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify(report, null, 2));
}

main().catch((error) => {
  console.error(`AWS seed failed: ${error.message}`);
  process.exitCode = 1;
});
