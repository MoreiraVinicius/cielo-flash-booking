import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { queries: { executor: 'constant-vus', vus: 5, duration: '15s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const commandBaseUrl = __ENV.COMMAND_BASE_URL || 'http://localhost:8082';
const queryBaseUrl = __ENV.QUERY_BASE_URL || 'http://localhost:8081';
const idempotencyKey = () => `${Date.now()}-${Math.random()}`;

export function setup() {
  const response = http.post(`${commandBaseUrl}/events`, JSON.stringify({ name: 'query benchmark', capacity: 10000 }), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
  });
  check(response, { 'benchmark event created': (result) => result.status === 201 });
  return response.json('id');
}

export default function (eventId) {
  const response = http.get(`${queryBaseUrl}/events/${eventId}`);
  check(response, { 'event query succeeded': (result) => result.status === 200 });
}
