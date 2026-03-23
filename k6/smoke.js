import { sleep } from 'k6';
import { postSync } from './common.js';

export const options = {
  scenarios: {
    smoke_sync: {
      executor: 'constant-vus',
      vus: 10,
      duration: __ENV.DURATION || '60s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    'http_req_duration{endpoint:sync-and-compare}': ['p(95)<15000'],
  },
};

export default function () {
  postSync();
  sleep(1);
}
