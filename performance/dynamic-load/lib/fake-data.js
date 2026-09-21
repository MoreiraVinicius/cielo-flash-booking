const MAX_NAME_LENGTH = 200;
const MAX_EMAIL_LENGTH = 320;
const MAX_IDEMPOTENCY_KEY_LENGTH = 128;

function normalizeSeed(seed) {
  const parsed = Number.parseInt(String(seed), 10);
  if (!Number.isSafeInteger(parsed)) {
    throw new Error('LOAD_SEED must be a safe integer');
  }
  return parsed >>> 0;
}

function hash32(value) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function mulberry32(seed) {
  let state = seed >>> 0;
  return () => {
    state += 0x6D2B79F5;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function sanitizeToken(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
}

function within(value, maximum) {
  return value.length <= maximum ? value : value.slice(0, maximum);
}

export function createGenerator(seed, runId) {
  const normalizedSeed = normalizeSeed(seed);
  const normalizedRunId = sanitizeToken(runId);
  if (!normalizedRunId) {
    throw new Error('runId must contain at least one alphanumeric character');
  }

  function randomFor(scope) {
    return mulberry32(hash32(`${normalizedSeed}:${normalizedRunId}:${scope}`));
  }

  return {
    eventFixture(index, plannedCommands) {
      if (!Number.isInteger(index) || index < 0 || index >= 12) {
        throw new Error('event index must be between 0 and 11');
      }
      if (!Number.isSafeInteger(plannedCommands) || plannedCommands < 1) {
        throw new Error('plannedCommands must be a positive safe integer');
      }
      const capacity = Math.max(100, plannedCommands + 100);
      return {
        name: within(`load-${normalizedRunId}-event-${index + 1}`, MAX_NAME_LENGTH),
        capacity,
      };
    },

    customer(vu, iteration) {
      if (!Number.isInteger(vu) || vu < 1 || !Number.isInteger(iteration) || iteration < 0) {
        throw new Error('vu must be positive and iteration must be non-negative integers');
      }
      const random = randomFor(`customer:${vu}:${iteration}`);
      const suffix = Math.floor(random() * 1000000000).toString(36);
      const localPart = `load-${normalizedRunId}-vu-${vu}-i-${iteration}-${suffix}`;
      return {
        name: within(`Load User ${vu} ${iteration} ${suffix}`, MAX_NAME_LENGTH),
        email: within(`${localPart}@example.com`, MAX_EMAIL_LENGTH),
      };
    },

    idempotencyKey(operation, vu, iteration) {
      const normalizedOperation = sanitizeToken(operation);
      if (!normalizedOperation || !Number.isInteger(vu) || vu < 1 || !Number.isInteger(iteration) || iteration < 0) {
        throw new Error('operation, vu and iteration are required for idempotency keys');
      }
      const entropy = hash32(`${normalizedSeed}:${normalizedRunId}:${normalizedOperation}:${vu}:${iteration}`).toString(36);
      return within(`load-${normalizedRunId}-${normalizedOperation}-vu-${vu}-i-${iteration}-${entropy}`, MAX_IDEMPOTENCY_KEY_LENGTH);
    },

    selectEvent(vu, iteration) {
      const random = randomFor(`event:${vu}:${iteration}`)();
      if (random < 0.6) {
        return { index: Math.floor(random / 0.2), tier: 'hot' };
      }
      if (random < 0.9) {
        return { index: 3 + Math.floor((random - 0.6) / 0.1), tier: 'warm' };
      }
      return { index: 6 + Math.floor((random - 0.9) / 0.016666666666666666), tier: 'cold' };
    },

    quantity(vu, iteration) {
      return randomFor(`quantity:${vu}:${iteration}`)() < 0.85 ? 1 : 2;
    },
  };
}

export const limits = {
  maxNameLength: MAX_NAME_LENGTH,
  maxEmailLength: MAX_EMAIL_LENGTH,
  maxIdempotencyKeyLength: MAX_IDEMPOTENCY_KEY_LENGTH,
};
