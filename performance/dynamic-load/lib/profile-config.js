const VALID_PROFILES = ['smoke', 'stress', 'spike', 'capacity', 'load', 'soak'];
const VALID_WORKLOADS = ['query-heavy', 'command-heavy', 'mixed'];
const MAX_RATE = 2000;

function positiveInteger(value, name) {
  const parsed = Number.parseInt(String(value), 10);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function boundedRate(value, name) {
  const rate = positiveInteger(value, name);
  if (rate > MAX_RATE) {
    throw new Error(`${name} must not exceed ${MAX_RATE}`);
  }
  return rate;
}

function loopbackUrl(value) {
  return /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?(?:\/|$)/.test(String(value));
}

function parseCommandUrls(value) {
  if (Array.isArray(value)) {
    return value;
  }
  return String(value || '').split(',').map((url) => url.trim()).filter(Boolean);
}

function defaultWhenMissing(value, fallback) {
  return value === undefined || value === null || value === '' ? fallback : value;
}

function scenario(name, executor, options) {
  const scenarios = {};
  scenarios[name] = Object.assign({ executor }, options);
  return scenarios;
}

export function loadConfig(input) {
  const profile = input.profile || 'smoke';
  const workload = input.workload || 'mixed';
  if (VALID_PROFILES.indexOf(profile) < 0) {
    throw new Error(`profile must be one of ${VALID_PROFILES.join(', ')}`);
  }
  if (VALID_WORKLOADS.indexOf(workload) < 0) {
    throw new Error(`workload must be one of ${VALID_WORKLOADS.join(', ')}`);
  }

  const queryBaseUrl = input.queryBaseUrl || 'http://127.0.0.1:8081';
  const commandBaseUrls = parseCommandUrls(input.commandBaseUrls || input.commandBaseUrl || 'http://127.0.0.1:8082');
  if (!loopbackUrl(queryBaseUrl) || commandBaseUrls.some((url) => !loopbackUrl(url))) {
    throw new Error('only loopback URLs are accepted');
  }
  if (profile !== 'smoke' && commandBaseUrls.length < 2) {
    throw new Error('non-smoke profiles require at least two command-api URLs');
  }

  const config = {
    profile,
    workload,
    queryBaseUrl,
    commandBaseUrls,
    seed: positiveInteger(defaultWhenMissing(input.seed, 20260921), 'seed'),
    runId: input.runId || 'local-run',
    startRate: boundedRate(defaultWhenMissing(input.startRate, 20), 'startRate'),
    stepRate: boundedRate(defaultWhenMissing(input.stepRate, 20), 'stepRate'),
    maxRate: boundedRate(defaultWhenMissing(input.maxRate, 200), 'maxRate'),
    sustainableRate: input.sustainableRate ? boundedRate(input.sustainableRate, 'sustainableRate') : null,
    forecastRate: input.forecastRate ? boundedRate(input.forecastRate, 'forecastRate') : null,
    forecastSource: input.forecastSource || null,
  };
  if (config.startRate > config.maxRate) {
    throw new Error('startRate must not exceed maxRate');
  }
  if (profile === 'load' && (!config.forecastRate || !config.forecastSource)) {
    throw new Error('load requires forecastRate and forecastSource');
  }
  if (['spike', 'capacity', 'soak'].indexOf(profile) >= 0 && !config.sustainableRate) {
    throw new Error(`${profile} requires sustainableRate`);
  }
  return config;
}

function stressStages(config) {
  const stages = [{ target: config.startRate, duration: '30s' }];
  for (let rate = config.startRate; rate <= config.maxRate; rate += config.stepRate) {
    stages.push({ target: rate, duration: '60s' });
  }
  return stages;
}

export function buildOptions(config) {
  const thresholds = {
    technical_failure_rate: ['rate<0.01'],
    'http_req_duration{operation:query}': ['p(95)<500'],
    'http_req_duration{operation:command}': ['p(95)<750'],
  };
  if (config.profile === 'smoke') {
    return { scenarios: scenario('smoke', 'shared-iterations', { vus: 1, iterations: 1, maxDuration: '60s' }), thresholds };
  }
  if (config.profile === 'stress') {
    return { scenarios: scenario('stress', 'ramping-arrival-rate', { startRate: 0, timeUnit: '1s', preAllocatedVUs: 20, maxVUs: 200, stages: stressStages(config) }), thresholds };
  }
  const rate = config.profile === 'load' ? config.forecastRate : Math.max(1, Math.floor(config.sustainableRate * 0.6));
  if (config.profile === 'spike') {
    return {
      scenarios: scenario('spike', 'ramping-arrival-rate', {
        startRate: Math.max(1, Math.floor(config.sustainableRate * 0.2)),
        timeUnit: '1s',
        preAllocatedVUs: 20,
        maxVUs: 200,
        stages: [
          { target: Math.max(1, Math.floor(config.sustainableRate * 0.2)), duration: '30s' },
          { target: Math.ceil(config.sustainableRate * 1.2), duration: '60s' },
          { target: Math.max(1, Math.floor(config.sustainableRate * 0.2)), duration: '60s' },
        ],
      }),
      thresholds,
    };
  }
  const duration = config.profile === 'soak' ? '2h' : config.profile === 'capacity' ? '30m' : '30m';
  return { scenarios: scenario(config.profile, 'constant-arrival-rate', { rate, timeUnit: '1s', duration, preAllocatedVUs: 20, maxVUs: 200 }), thresholds };
}

export const limits = { maxRate: MAX_RATE, profiles: VALID_PROFILES, workloads: VALID_WORKLOADS };
