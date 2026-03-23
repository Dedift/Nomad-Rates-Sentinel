# k6 Load Tests

These scripts target the deployed Render service or a local instance.

## Before running on Render

For a real break test of `POST /sync-and-compare`, temporarily disable the 30-second in-memory cache on the target deployment:

```bash
RATES_CACHE_SYNC_TTL=0s
```

If you keep the default `30s`, the stress test will mostly measure cache hits inside each instance instead of real sync pressure.

## Install k6

```bash
choco install k6
```

## Environment

```bash
$env:BASE_URL="https://nomad-rates-sentinel.onrender.com"
```

Optional:

```bash
$env:COMPARE_CODES="USD,EUR,RUB,GBP,CNY,BYN"
```

## 1. Smoke test

10 users update rates concurrently.

```bash
k6 run -e BASE_URL=$env:BASE_URL .\k6\smoke.js
```

## 2. Stress test

200 virtual users hammer `POST /sync-and-compare` at the same time.

```bash
k6 run -e BASE_URL=$env:BASE_URL -e VUS=200 -e DURATION=60s .\k6\stress.js
```

## 3. Corruption test

While sync is running, readers continuously hit `GET /compare/{code}`.

```bash
k6 run -e BASE_URL=$env:BASE_URL -e SYNC_VUS=5 -e READ_VUS=150 -e DURATION=90s .\k6\corruption.js
```

## What to watch on Render

- memory usage
- response times
- 5xx rate
- container restarts
- whether compare requests stay responsive while sync is active

## Recommended sequence

1. Warm the service with one manual request.
2. Run `smoke.js`.
3. Run `corruption.js`.
4. Run `stress.js` last.
