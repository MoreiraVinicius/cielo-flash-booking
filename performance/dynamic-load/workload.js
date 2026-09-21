import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { createGenerator } from './lib/fake-data.js';
import { buildOptions, loadConfig } from './lib/profile-config.js';

const config = loadConfig({
  profile: __ENV.LOAD_PROFILE || 'smoke',
  workload: __ENV.LOAD_WORKLOAD || 'mixed',
  queryBaseUrl: __ENV.QUERY_BASE_URL,
  commandBaseUrls: __ENV.COMMAND_BASE_URLS,
  seed: __ENV.LOAD_SEED,
  runId: __ENV.LOAD_RUN_ID,
  startRate: __ENV.LOAD_START_RATE,
  stepRate: __ENV.LOAD_STEP_RATE,
  maxRate: __ENV.LOAD_MAX_RATE,
  sustainableRate: __ENV.LOAD_SUSTAINABLE_RATE,
  forecastRate: __ENV.LOAD_FORECAST_RATE,
  forecastSource: __ENV.LOAD_FORECAST_SOURCE,
});

export const options = buildOptions(config);

const technicalFailureRate = new Rate('technical_failure_rate');
const businessRejections = new Counter('business_rejections');
const completedLifecycle = new Counter('completed_reservation_lifecycles');
const generator = createGenerator(config.seed, config.runId);
const reservationsByVu = {};

function tag(operation, phase, target) {
  return { tags: { operation, phase: phase || config.profile, command_target: target || 'query-api' } };
}

function requestParams(headers, operation, phase, target) {
  const request = tag(operation, phase, target);
  request.headers = headers || {};
  return request;
}

function commandUrl() {
  const index = (__VU - 1) % config.commandBaseUrls.length;
  return { index, url: config.commandBaseUrls[index] };
}

function classify(response, label) {
  const technical = response.status === 0 || response.status >= 500;
  technicalFailureRate.add(technical);
  if (response.status >= 400 && response.status < 500) {
    businessRejections.add(1);
  }
  check(response, { [label]: (result) => result.status >= 200 && result.status < 300 });
  return !technical;
}

function createReservation(eventId, phase) {
  const target = commandUrl();
  const customer = generator.customer(__VU, __ITER);
  const response = http.post(`${target.url}/events/${eventId}/reservations`, JSON.stringify({
    quantity: generator.quantity(__VU, __ITER),
    customer,
  }), requestParams({ 'Content-Type': 'application/json', 'Idempotency-Key': generator.idempotencyKey('reserve', __VU, __ITER) }, 'command', phase, `command-api-${target.index + 1}`));
  classify(response, 'reservation command completed');
  if (response.status === 201) {
    const reservationId = response.json('id');
    if (reservationId) {
      reservationsByVu[__VU] = reservationsByVu[__VU] || [];
      reservationsByVu[__VU].push(reservationId);
      return reservationId;
    }
  }
  return null;
}

function queryEvent(eventId, phase) {
  const response = http.get(`${config.queryBaseUrl}/events/${eventId}`, requestParams({}, 'query', phase));
  classify(response, 'event query completed');
}

function queryReservation(reservationId, phase) {
  const response = http.get(`${config.queryBaseUrl}/reservations/${reservationId}`, requestParams({}, 'query', phase));
  classify(response, 'reservation query completed');
}

function cancelReservation(reservationId, phase) {
  const target = commandUrl();
  const response = http.del(`${target.url}/reservations/${reservationId}`, null, requestParams({ 'Idempotency-Key': generator.idempotencyKey('cancel', __VU, __ITER) }, 'command', phase, `command-api-${target.index + 1}`));
  classify(response, 'reservation cancellation completed');
}

function selectedOperation() {
  if (config.workload === 'query-heavy') {
    return 'event-query';
  }
  if (config.workload === 'command-heavy') {
    return 'reservation-create';
  }
  const bucket = (Math.imul(__VU + 31, __ITER + 17) >>> 0) % 100;
  if (bucket < 70) {
    return 'event-query';
  }
  if (bucket < 90) {
    return 'reservation-create';
  }
  if (bucket < 95) {
    return 'reservation-query';
  }
  return 'reservation-cancel';
}

export function setup() {
  const eventIds = [];
  for (let index = 0; index < 12; index += 1) {
    const target = config.commandBaseUrls[0];
    const response = http.post(`${target}/events`, JSON.stringify(generator.eventFixture(index, config.maxRate * 60)), requestParams({ 'Content-Type': 'application/json', 'Idempotency-Key': generator.idempotencyKey(`event-${index}`, 1, index) }, 'command', 'setup', 'command-api-1'));
    if (response.status !== 201) {
      fail(`could not create dynamic event ${index + 1}: ${response.status}`);
    }
    eventIds.push(response.json('id'));
  }
  return { eventIds };
}

export default function (data) {
  if (config.profile === 'smoke') {
    const eventId = data.eventIds[0];
    queryEvent(eventId, 'smoke');
    const reservationId = createReservation(eventId, 'smoke');
    if (!reservationId) {
      fail('smoke could not create reservation');
    }
    queryReservation(reservationId, 'smoke');
    cancelReservation(reservationId, 'smoke');
    completedLifecycle.add(1);
    return;
  }

  const selected = generator.selectEvent(__VU, __ITER);
  const eventId = data.eventIds[selected.index];
  const phase = config.profile;
  const operation = selectedOperation();
  if (operation === 'event-query') {
    queryEvent(eventId, phase);
    return;
  }
  if (operation === 'reservation-create') {
    createReservation(eventId, phase);
    return;
  }
  const reservations = reservationsByVu[__VU] || [];
  if (reservations.length === 0) {
    queryEvent(eventId, phase);
    return;
  }
  const reservationId = reservations[reservations.length - 1];
  if (operation === 'reservation-query') {
    queryReservation(reservationId, phase);
    return;
  }
  cancelReservation(reservationId, phase);
  reservations.pop();
}

export function handleSummary(data) {
  return { stdout: JSON.stringify({ profile: config.profile, workload: config.workload, metrics: data.metrics }, null, 2) };
}
