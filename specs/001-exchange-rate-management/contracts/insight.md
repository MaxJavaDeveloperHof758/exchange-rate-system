# API Contract: AI Trend Insight Endpoint

Backs User Story 2's AI-insight half. See [research.md](../research.md) Decision 2 for the model
choice, [spec.md](../spec.md) FR-015–FR-017 and NFR-005, and constitution Principle X for the
grounding/prompt-design requirements this contract must satisfy.

## `GET /api/exchange/insight`

Generates a short natural-language commentary on the rate trend for a currency pair and date
range, grounded in the actual stored rate data for that exact range.

**Query Parameters**:

| Name | Type | Required | Notes |
|---|---|---|---|
| `from` | string (3-letter currency code) | Yes | Source currency. |
| `to` | string (3-letter currency code) | Yes | Target currency. |
| `fromDate` | string (`YYYY-MM-DD`) | Yes | Inclusive start of range. |
| `toDate` | string (`YYYY-MM-DD`) | Yes | Inclusive end of range. |

**Success — `200 OK`** (shape follows brief Appendix A's suggested design):

```json
{
  "from": "EUR",
  "to": "GBP",
  "fromDate": "2024-02-01",
  "toDate": "2024-03-01",
  "insight": "EUR/GBP softened by approximately 1.8% over this period, with the steepest decline in the final week of February."
}
```

**Errors**:

| Status | Condition | Body |
|---|---|---|
| `400 Bad Request` | Unknown currency code, malformed date, or `fromDate` after `toDate` | `{ "error": "...", "message": "..." }` |
| `404 Not Found` | No stored rate data exists anywhere in the requested range (nothing to summarize) | `{ "error": "RATE_NOT_AVAILABLE", "message": "..." }` |
| `503 Service Unavailable` | The local LLM call failed or timed out (model not running, connection refused, etc.) | `{ "error": "INSIGHT_UNAVAILABLE", "message": "The trend insight could not be generated. The rate data itself is still available via /api/exchange/history." }` |

**Prompt construction contract** (what the backend MUST do internally to satisfy constitution
Principle X and rubric "Prompt design," regardless of implementation detail):
1. Fetch the same `(date, rate)` series that `/api/exchange/history` would return for this exact
   `from`/`to`/`fromDate`/`toDate` — the model receives the real numbers, never a paraphrase or a
   subset chosen for narrative convenience.
2. The system prompt instructs the model to: reference only the provided data, stay within a
   short length (2–4 sentences), avoid generic financial-advice framing or disclaimers, and avoid
   producing a response that would be equally true regardless of the input data (no filler).
3. A single-day range (`fromDate == toDate`) MUST be described as a single observation, not framed
   as a multi-day trend (spec.md User Story 2, Acceptance Scenario 6).

**Behavioral notes**:
- This endpoint is independent of `/api/exchange/history` at the HTTP level — the frontend calls
  both in parallel so the table/chart can render before the (typically slower) insight arrives,
  each with its own loading state (FR-017).
- This endpoint has no side effects on usage counters (out of scope for FR-011, which is
  `/api/exchange`-specific) and is not cached/persisted server-side (data-model.md's `Trend
  Insight` entity is explicitly ephemeral).
