import test from 'node:test';
import assert from 'node:assert/strict';
import { COMMANDS_PER_SECOND, CUSTOMER_COUNT, createCustomer, parseConfig, scheduleAtMostFourCommandsPerSecond } from './lib.mjs';

test('requires an HTTPS AWS invocation configuration', () => {
  assert.throws(() => parseConfig(new Map([['--api-url', 'http://example.test'], ['--region', 'sa-east-1'], ['--profile', 'demo'], ['--role-arn', 'arn:aws:iam::123456789012:role/ApiInvoker']])), /HTTPS/);
  assert.deepEqual(parseConfig(new Map([['--api-url', 'https://example.execute-api.sa-east-1.amazonaws.com/demo'], ['--region', 'sa-east-1'], ['--profile', 'demo'], ['--role-arn', 'arn:aws:iam::123456789012:role/ApiInvoker']])).region, 'sa-east-1');
});

test('generates one thousand unique non-routable customer emails', () => {
  const customers = Array.from({ length: CUSTOMER_COUNT }, (_, index) => createCustomer('20260924000000', index));
  assert.equal(new Set(customers.map((customer) => customer.email)).size, CUSTOMER_COUNT);
  assert.ok(customers.every((customer) => customer.email.endsWith('@example.test')));
  assert.ok(customers.every((customer) => customer.name.length > 0));
});

test('schedules a maximum of four command starts per second', async () => {
  const scheduledDelays = [];
  const observed = [];
  let virtualNow = 0;
  await scheduleAtMostFourCommandsPerSecond(3, (index) => observed.push(index), async (milliseconds) => { scheduledDelays.push(milliseconds); virtualNow += milliseconds; }, () => virtualNow);
  assert.deepEqual(observed, [0, 1, 2]);
  assert.equal(COMMANDS_PER_SECOND, 4);
  assert.deepEqual(scheduledDelays, [250, 250]);
});
