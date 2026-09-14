import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: { mixed: { executor: 'constant-vus', vus: 4, duration: '15s' } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<750'] },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const commandBaseUrls = (__ENV.COMMAND_BASE_URLS || __ENV.COMMAND_BASE_URL || 'http://localhost:8082')
  .split(',')
  .map((url) => url.trim())
  .filter(Boolean);
const queryBaseUrl = __ENV.QUERY_BASE_URL || 'http://localhost:8081';
const idempotencyKey = () => `${Date.now()}-${Math.random()}`;

function commandTarget() {
  const index = (__VU - 1) % commandBaseUrls.length;
  return { index, url: commandBaseUrls[index] };
}

export function setup() {
  const response = http.post(`${commandBaseUrls[0]}/events`, JSON.stringify({ name: 'mixed benchmark', capacity: 10000 }), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
  });
  check(response, { 'benchmark event created': (result) => result.status === 201 });
  return response.json('id');
}

export default function (eventId) {
  const query = http.get(`${queryBaseUrl}/events/${eventId}`);
  check(query, { 'mixed query succeeded': (result) => result.status === 200 });

  if (__ITER % 3 === 0) {
    const target = commandTarget();
    const payload = {
      quantity: 1,
      customer: { name: `Mixed ${__VU}`, email: `mixed-${__VU}-${__ITER}@example.com` },
    };
    const command = http.post(`${target.url}/events/${eventId}/reservations`, JSON.stringify(payload), {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey() },
      tags: { command_target: `command-api-${target.index + 1}` },
    });
    check(command, {
      [`mixed reservation via command-api-${target.index + 1}`]: (result) => result.status === 201,
    });
  }
}
