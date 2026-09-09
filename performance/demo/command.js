import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { reservations: { executor: 'constant-vus', vus: 3, duration: '15s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<750'] },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const commandBaseUrl = __ENV.COMMAND_BASE_URL || 'http://localhost:8082';
const idempotencyKey = () => `${Date.now()}-${Math.random()}`;

export function setup() {
  const response = http.post(`${commandBaseUrl}/events`, JSON.stringify({ name: 'command benchmark', capacity: 10000 }), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
  });
  check(response, { 'benchmark event created': (result) => result.status === 201 });
  return response.json('id');
}

export default function (eventId) {
  const payload = {
    quantity: 1,
    customer: { name: `Benchmark ${__VU}`, email: `benchmark-${__VU}-${__ITER}@example.com` },
  };
  const response = http.post(`${commandBaseUrl}/events/${eventId}/reservations`, JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
  });
  check(response, { 'reservation created without oversell': (result) => result.status === 201 });
}
