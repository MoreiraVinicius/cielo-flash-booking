import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { reservations: { executor: 'constant-vus', vus: 3, duration: '15s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<750'] },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const commandBaseUrls = (__ENV.COMMAND_BASE_URLS || __ENV.COMMAND_BASE_URL || 'http://localhost:8082')
  .split(',')
  .map((url) => url.trim())
  .filter(Boolean);
const idempotencyKey = () => `${Date.now()}-${Math.random()}`;

function commandTarget() {
  const index = (__VU - 1) % commandBaseUrls.length;
  return { index, url: commandBaseUrls[index] };
}

export function setup() {
  const response = http.post(`${commandBaseUrls[0]}/events`, JSON.stringify({ name: 'command benchmark', capacity: 10000 }), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
  });
  check(response, { 'benchmark event created': (result) => result.status === 201 });
  return response.json('id');
}

export default function (eventId) {
  const target = commandTarget();
  const payload = {
    quantity: 1,
    customer: { name: `Benchmark ${__VU}`, email: `benchmark-${__VU}-${__ITER}@example.com` },
  };
  const response = http.post(`${target.url}/events/${eventId}/reservations`, JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
    tags: { command_target: `command-api-${target.index + 1}` },
  });
  check(response, {
    [`reservation created via command-api-${target.index + 1}`]: (result) => result.status === 201,
  });
}
