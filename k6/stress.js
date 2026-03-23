import { sleep } from 'k6';
import { postSync } from './common.js';

export const options = {
  scenarios: {
    sync_stress: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 200),
      duration: __ENV.DURATION || '60s',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.25'],
    'http_req_duration{endpoint:sync-and-compare}': ['p(95)<30000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

export default function () {
  postSync();
  sleep(Number(__ENV.SLEEP_SECONDS || 0.5));
}
