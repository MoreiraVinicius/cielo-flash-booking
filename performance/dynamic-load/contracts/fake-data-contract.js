import { check, fail } from 'k6';
import { createGenerator, limits } from '../lib/fake-data.js';

function hash(value) {
  let result = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 16777619);
  }
  return (result >>> 0).toString(16);
}

function sequence(generator) {
  return Array.from({ length: 250 }, (_, index) => ({
    event: generator.selectEvent((index % 7) + 1, index),
    customer: generator.customer((index % 7) + 1, index),
    quantity: generator.quantity((index % 7) + 1, index),
    key: generator.idempotencyKey('reserve', (index % 7) + 1, index),
  }));
}

export const options = { vus: 1, iterations: 1 };

export default function () {
  const first = createGenerator(20260921, 'contract-run');
  const second = createGenerator(20260921, 'contract-run');
  const firstSequence = sequence(first);
  const secondSequence = sequence(second);
  const distribution = { hot: 0, warm: 0, cold: 0 };
  const keys = new Set();

  for (let iteration = 0; iteration < 10000; iteration += 1) {
    distribution[first.selectEvent((iteration % 23) + 1, iteration).tier] += 1;
    keys.add(first.idempotencyKey('reserve', (iteration % 23) + 1, iteration));
  }

  const example = firstSequence[0];
  const event = first.eventFixture(0, 10000);
  const assertions = check(null, {
    'same seed produces same sequence hash': () => hash(JSON.stringify(firstSequence)) === hash(JSON.stringify(secondSequence)),
    'event names and customer names respect API limit': () => event.name.length <= limits.maxNameLength && example.customer.name.length <= limits.maxNameLength,
    'customer e-mail uses example.com and respects API limit': () => example.customer.email.endsWith('@example.com') && example.customer.email.length <= limits.maxEmailLength,
    'quantities are positive': () => firstSequence.every((item) => item.quantity > 0),
    'idempotency keys are unique and bounded': () => keys.size === 10000 && Array.from(keys).every((key) => key.length <= limits.maxIdempotencyKeyLength),
    'hot distribution is 60 percent plus or minus two points': () => distribution.hot >= 5800 && distribution.hot <= 6200,
    'warm distribution is 30 percent plus or minus two points': () => distribution.warm >= 2800 && distribution.warm <= 3200,
    'cold distribution is 10 percent plus or minus two points': () => distribution.cold >= 800 && distribution.cold <= 1200,
  });

  if (!Object.values(assertions).every(Boolean)) {
    fail('fake-data contract failed');
  }
}
