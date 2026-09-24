import { faker } from '@faker-js/faker';

export const CUSTOMER_COUNT = 1000;
export const COMMANDS_PER_SECOND = 4;

export function parseConfig(argumentsByName) {
  const apiUrl = argumentsByName.get('--api-url');
  const region = argumentsByName.get('--region');
  const profile = argumentsByName.get('--profile');
  const roleArn = argumentsByName.get('--role-arn');
  if (!apiUrl || !region || !profile || !roleArn) {
    throw new Error('requires --api-url, --region, --profile, and --role-arn');
  }
  const endpoint = new URL(apiUrl);
  if (endpoint.protocol !== 'https:') {
    throw new Error('api-url must use HTTPS');
  }
  if (!roleArn.startsWith('arn:aws:iam::')) {
    throw new Error('role-arn must be an IAM role ARN');
  }
  return { endpoint, profile, region, roleArn };
}

export function createRunId(now = new Date()) {
  return now.toISOString().replace(/[-:.TZ]/g, '').slice(0, 14);
}

export function createCustomer(runId, index) {
  if (!Number.isInteger(index) || index < 0 || index >= CUSTOMER_COUNT) {
    throw new Error(`customer index must be between 0 and ${CUSTOMER_COUNT - 1}`);
  }
  return {
    name: faker.person.fullName(),
    email: `aws-seed-${runId}-${index}@example.test`,
  };
}

export async function scheduleAtMostFourCommandsPerSecond(count, action, sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)), now = () => Date.now()) {
  const intervalMilliseconds = 1000 / COMMANDS_PER_SECOND;
  const startedAt = now();
  const pending = [];
  let failure;
  for (let index = 0; index < count; index += 1) {
    const scheduledAt = startedAt + (index * intervalMilliseconds);
    const delay = scheduledAt - now();
    if (delay > 0) {
      await sleep(delay);
    }
    if (failure) {
      break;
    }
    pending.push(Promise.resolve(action(index)).catch((error) => {
      failure = error;
      throw error;
    }));
  }
  await Promise.all(pending);
  if (failure) {
    throw failure;
  }
}
