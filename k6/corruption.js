import { sleep } from 'k6';
import { getCompare, postSync, randomCode } from './common.js';

export const options = {
  scenarios: {
    sync_writers: {
      executor: 'constant-vus',
      exec: 'writers',
      vus: Number(__ENV.SYNC_VUS || 5),
      duration: __ENV.DURATION || '90s',
      gracefulStop: '10s',
    },
    compare_readers: {
      executor: 'constant-vus',
      exec: 'readers',
      vus: Number(__ENV.READ_VUS || 150),
      duration: __ENV.DURATION || '90s',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    'http_req_failed{endpoint:compare}': ['rate<0.05'],
    'http_req_duration{endpoint:compare}': ['p(95)<5000'],
    'http_req_duration{endpoint:sync-and-compare}': ['p(95)<30000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

export function writers() {
  postSync();
  sleep(Number(__ENV.WRITER_SLEEP_SECONDS || 1));
}

export function readers() {
  getCompare(randomCode());
  sleep(Number(__ENV.READER_SLEEP_SECONDS || 0.2));
}
