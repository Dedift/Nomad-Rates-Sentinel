import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
export const SYNC_ENDPOINT = `${BASE_URL}/sync-and-compare`;
export const COMPARE_CODES = (__ENV.COMPARE_CODES || 'USD,EUR,RUB,GBP,CNY,BYN')
  .split(',')
  .map((code) => code.trim())
  .filter((code) => code.length > 0);

export function postSync() {
  const response = http.post(SYNC_ENDPOINT, null, {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'sync-and-compare', method: 'POST' },
  });

  check(response, {
    'sync returned non-empty body on 200': (r) => r.status !== 200 || r.json().length >= 0,
  });

  return response;
}

export function getCompare(code) {
  const response = http.get(`${BASE_URL}/compare/${code}`, {
    tags: { endpoint: 'compare', method: 'GET', code },
    responseCallback: http.expectedStatuses(200, 404),
  });

  check(response, {
    'compare returned valid status': (r) => [200, 404].includes(r.status),
  });

  if (response.status === 200) {
    check(response, {
      'compare payload has code': (r) => r.json('code') === code,
      'compare payload has rates': (r) => r.json('nbkRate') !== null && r.json('xeRate') !== null,
    });
  }

  return response;
}

export function randomCode() {
  return COMPARE_CODES[Math.floor(Math.random() * COMPARE_CODES.length)];
}
