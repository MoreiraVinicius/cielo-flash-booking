import { check, fail } from 'k6';
import { buildOptions, limits, loadConfig } from '../lib/profile-config.js';

function invalid(input, expectedMessage) {
  try {
    loadConfig(input);
    return false;
  } catch (error) {
    return String(error).indexOf(expectedMessage) >= 0;
  }
}

export const options = { vus: 1, iterations: 1 };

export default function () {
  const smoke = loadConfig({ profile: 'smoke', workload: 'mixed' });
  const stress = loadConfig({ profile: 'stress', workload: 'mixed', commandBaseUrls: ['http://127.0.0.1:8082', 'http://127.0.0.1:8083'], maxRate: 60, stepRate: 20 });
  const spike = loadConfig({ profile: 'spike', workload: 'mixed', commandBaseUrls: ['http://127.0.0.1:8082', 'http://127.0.0.1:8083'], sustainableRate: 100 });
  const load = loadConfig({ profile: 'load', workload: 'mixed', commandBaseUrls: ['http://127.0.0.1:8082', 'http://127.0.0.1:8083'], forecastRate: 75, forecastSource: 'approved-forecast-2026-09' });
  const assertions = check(null, {
    'smoke is exactly one VU and one iteration': () => buildOptions(smoke).scenarios.smoke.vus === 1 && buildOptions(smoke).scenarios.smoke.iterations === 1 && buildOptions(smoke).scenarios.smoke.maxDuration === '60s',
    'stress has a 30 second warmup and 60 second steps': () => buildOptions(stress).scenarios.stress.executor === 'ramping-arrival-rate' && buildOptions(stress).scenarios.stress.stages[0].duration === '30s' && buildOptions(stress).scenarios.stress.stages.slice(1).every((stage) => stage.duration === '60s'),
    'spike follows 20 120 20 waveform': () => buildOptions(spike).scenarios.spike.stages[0].target === 20 && buildOptions(spike).scenarios.spike.stages[1].target === 120 && buildOptions(spike).scenarios.spike.stages[2].target === 20,
    'load requires approved rate and source': () => buildOptions(load).scenarios.load.rate === 75 && invalid({ profile: 'load', commandBaseUrls: ['http://127.0.0.1:8082', 'http://127.0.0.1:8083'] }, 'load requires forecastRate and forecastSource'),
    'non-smoke requires two command APIs': () => invalid({ profile: 'stress' }, 'non-smoke profiles require at least two command-api URLs'),
    'unsafe targets fail before HTTP': () => invalid({ profile: 'smoke', queryBaseUrl: 'https://example.com' }, 'only loopback URLs are accepted'),
    'invalid rates and safe ceiling fail': () => invalid({ profile: 'smoke', maxRate: 0 }, 'maxRate must be a positive integer') && invalid({ profile: 'smoke', maxRate: limits.maxRate + 1 }, 'maxRate must not exceed'),
  });
  if (!Object.values(assertions).every(Boolean)) {
    fail('profile contract failed');
  }
}
