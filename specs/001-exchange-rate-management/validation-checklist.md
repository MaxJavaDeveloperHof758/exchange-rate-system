# T054 Manual Validation Checklist

Companion to [`quickstart.md`](quickstart.md) for T054 ("Execute the full quickstart.md
validation manually end-to-end ... and record/fix any discrepancy found"). Quickstart says *what*
should happen at a high level; this document gives concrete commands, expected output, and
checkboxes so the run can actually be ticked off step by step. It does not replace quickstart.md —
read that first for the narrative/rationale behind each step.

**This checklist was prepared, not executed** — code-level discrepancies below were found by
reading the implementation, not by running the full flow. Steps 1–7 still need a real run by you.

---

## 0. Discrepancies found (and their fixes)

| # | Discrepancy | Status |
|---|---|---|
| 1 | Quickstart Step 6.3 said to confirm the currencies from **Step 3** appear in `/api/analytics`. Step 3's two calls (`date=1999-01-01` → `404`, `from=XXX` → `400`) are both deliberately unsuccessful, and `UsageTrackingService` is only invoked "on success" (T027) — so EUR/PLN from Step 3 alone would never appear. Confirmed by reading `ExchangeRateController`/`UsageTrackingService`. | **Fixed** in `quickstart.md` this same change — reworded to state Step 3 must **not** show up, turning it into a real negative-check. |
| 2 | Quickstart's Prerequisites section points a reviewer to "the root README for the exact [JDK] version" and to "the root README's AI Workflow/model setup section" for Ollama/OpenAI config. `README.md` is still the original placeholder — every relevant section (`Setup & Run`, `AI Workflow`) is literally `_TODO_`. A reviewer following quickstart.md today hits a dead end at both pointers. | **Not fixed here** — this is T055's job (writing the README), not T054's (validating quickstart.md's own steps). Flagging it so it isn't lost: `pom.xml` pins `<java.version>25</java.version>`; Ollama's model comes from `OLLAMA_MODEL` (default `llama3.2`, `application.yml`). |
| 3 | Everything else checked below (env-var requirement, `application-dev.yml`, ports, CORS origin, error-code strings, `XXX` rejection, analytics response shape) matched quickstart's claims exactly — no other fix needed. | Verified by reading code, not by running it — re-confirm live during the steps below. |

---

## 1. Backend startup (quickstart Step 1)

```bash
cd backend
export FIXER_API_KEY=your-fixer-io-key   # required — startup fails without it (WebClientConfig eagerly binds it)
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

- [ ] Startup log ends with `Started ExchangeRateSystemApplication...` and no stack trace.
- [ ] `curl -s http://localhost:8080/v3/api-docs | head -c 200` returns JSON (not a 404/500).
- [ ] Swagger UI loads: open `http://localhost:8080/swagger-ui/index.html` and confirm
      `/api/exchange`, `/api/exchange/history`, `/api/exchange/refresh`, `/api/exchange/insight`,
      and `/api/analytics` are all listed.

If `FIXER_API_KEY` is unset, startup should fail fast with a property-placeholder resolution
error, not hang or silently start half-broken — worth confirming once, since it's the one
prerequisite most likely to be forgotten on a fresh checkout.

## 2. Confirm seeded data (quickstart Step 2)

```bash
curl -s "http://localhost:8080/api/exchange?from=EUR&to=PLN" | python3 -m json.tool
```

- [ ] `200 OK`.
- [ ] `exchange` is exactly `4.49781250000000000000` (not the brief's illustrative `4.44` — see
      quickstart.md Step 2 for why: PLN sits in the real 2.75% "other currencies" tier, not the
      brief's illustrative 4% tier).
- [ ] `date` is today's date (DevDataSeeder seeds through today, per `backend/src/main/java/.../config/DevDataSeeder.java`).
- [ ] `fromQueryCount`/`toQueryCount` are both `1` on a completely fresh `./data/exchangedb` file;
      on a reused data file they'll be whatever they already were — this call itself adds exactly
      1 to each, which matters for the delta math in Step 4 below.

## 3. Error paths — and that they leave zero trace in analytics (quickstart Step 3)

```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/exchange?from=EUR&to=PLN&date=1999-01-01"
curl -s "http://localhost:8080/api/exchange?from=EUR&to=PLN&date=1999-01-01" | python3 -m json.tool
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/exchange?from=XXX&to=PLN"
curl -s "http://localhost:8080/api/exchange?from=XXX&to=PLN" | python3 -m json.tool
```

- [ ] First call: `404`, body `{"error": "RATE_NOT_AVAILABLE", ...}`.
- [ ] Second call: `400`, body `{"error": "UNKNOWN_CURRENCY", ...}`.
- [ ] Record EUR/PLN's `totalCount` from `GET /api/analytics` **before and after** these two
      calls — they must be identical. If either count moved, that's a real bug (usage tracking
      firing on a failed lookup), not an expected side effect.

## 4. Concurrency safety of usage counters (quickstart Step 4, NFR-002/SC-003)

Capture a baseline, fire 50 concurrent successful lookups, then diff:

```bash
#!/usr/bin/env bash
set -euo pipefail
BASE=http://localhost:8080/api

before=$(curl -s "$BASE/analytics")
eur_before=$(echo "$before" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((c['totalCount'] for c in d['topCurrencies'] if c['currency']=='EUR'), 0))")
pln_before=$(echo "$before" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((c['totalCount'] for c in d['topCurrencies'] if c['currency']=='PLN'), 0))")

for i in $(seq 1 50); do
  curl -s -o /dev/null "$BASE/exchange?from=EUR&to=PLN" &
done
wait

after=$(curl -s "$BASE/analytics")
eur_after=$(echo "$after" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((c['totalCount'] for c in d['topCurrencies'] if c['currency']=='EUR'), 0))")
pln_after=$(echo "$after" | python3 -c "import json,sys; d=json.load(sys.stdin); print(next((c['totalCount'] for c in d['topCurrencies'] if c['currency']=='PLN'), 0))")

echo "EUR: $eur_before -> $eur_after (delta $((eur_after - eur_before)))"
echo "PLN: $pln_before -> $pln_after (delta $((pln_after - pln_before)))"
```

- [ ] Both deltas are exactly `50` — not 49, not 51. Each successful `EUR/PLN` lookup increments
      *both* currencies once (T024), so a lost or double-counted increment under concurrent load
      would show up here as a delta that isn't exactly 50 on at least one side.
- [ ] Re-run the script a second time immediately after — deltas should again be exactly 50 each,
      confirming this isn't a one-off fluke.

(If `python3` isn't available, `jq -r '.topCurrencies[] | select(.currency=="EUR") | .totalCount'`
is an equivalent one-liner substitute for each `python3 -c ...` above.)

## 5. Idempotent ingestion (quickstart Step 7)

```bash
curl -s -X POST http://localhost:8080/api/exchange/refresh | python3 -m json.tool
curl -s -X POST http://localhost:8080/api/exchange/refresh | python3 -m json.tool
```

- [ ] Both calls return `202` with a timestamp (or `502` if `FIXER_API_KEY` isn't a real key — see
      note below).
- [ ] `GET /api/analytics` counts are **unchanged** by either call — `/refresh` must never call
      `UsageTrackingService` (T028).
- [ ] If real Fixer.io data lands, `GET /api/exchange/history?...` for that day still shows
      exactly one row per currency for that date, not two.

Note: with a placeholder `FIXER_API_KEY` (not a real Fixer.io key), expect `502 Bad Gateway`
(`UPSTREAM_FETCH_ERROR`) instead of `202` — that's `UpstreamFetchException` behaving correctly,
not a bug. Get a real free-tier key to exercise the actual success path.

## 6. Frontend + all three views (quickstart Steps 5–6)

```bash
cd frontend
npm install
ng serve
```

Open `http://localhost:4200`:

- [ ] **Calculator** (`/calculator`): submit `EUR` → `PLN`. Loading indicator appears briefly,
      then the rate + both query counts render. Submit an unsupported code (e.g. `ZZZ`) or leave
      a field blank — a distinct validation/error message appears, not a raw stack trace.
- [ ] **Historical Trend** (`/trend`): pick `EUR`/`PLN` and a date range covering the last 7 days
      (DevDataSeeder's window). Table and chart render side by side. Insight panel shows its own
      "Generating insight…" state, then either commentary or an error — independently of the
      table/chart (confirm the table/chart never show a loading spinner tied to the insight call).
- [ ] Stop Ollama (`ollama stop` / kill the serve process) and reload `/trend` with the same
      range: insight panel shows a distinct error state; table/chart are unaffected (NFR-005).
- [ ] Submit a single-day range (`startDate == endDate`) on `/trend`: the insight text describes
      a single observation, not a multi-day trend (contracts/insight.md's prompt-construction
      contract, point 3).
- [ ] **Analytics** (`/analytics`): EUR and PLN appear, ranked by `totalCount` descending, and the
      bar widths are visibly proportional to each other. Per the Step 3 fix above — this must
      reflect only Step 4's and this Calculator submission's successful lookups, not Step 3's
      failed ones.

## 7. Same-currency edge case (spec.md, not explicitly in quickstart)

```bash
curl -s "http://localhost:8080/api/exchange?from=EUR&to=EUR" | python3 -m json.tool
```

- [ ] `exchange` is `1` (or the base-tier-adjusted equivalent — same currency, zero spread).
- [ ] `GET /api/analytics`'s EUR `totalCount` increases by exactly **1** for this call, not 2 —
      T024 increments a currency once even when `from == to`.

## Success

All checkboxes above ticked, with the Section 0 discrepancy fixed and the README gap logged for
T055, is equivalent to a completed T054.
