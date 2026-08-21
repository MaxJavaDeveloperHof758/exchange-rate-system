# API Contract: Analytics Endpoint

Backs User Story 3 (Usage Analytics Dashboard). See [data-model.md](../data-model.md) for the
`Currency Usage Counter` entity and [spec.md](../spec.md) FR-011–FR-013.

## `GET /api/analytics`

Returns usage statistics across all currencies ever queried.

**Query Parameters**: none required.

**Success — `200 OK`** (shape follows brief Appendix A's suggested design):

```json
{
  "topCurrencies": [
    { "currency": "EUR", "totalCount": 142, "lastQueried": "2024-03-15" },
    { "currency": "USD", "totalCount": 98, "lastQueried": "2024-03-14" }
  ]
}
```

- `topCurrencies` is sorted by `totalCount` descending (spec.md Acceptance Scenario: "the
  most-queried currencies are visibly distinguishable from the least-queried ones," User Story 3,
  Scenario 2).
- A currency that has never been queried does not appear in this list at all (spec.md User Story
  3, Scenario 3) — the frontend renders its absence as zero, not as an error or undefined row.
- `lastQueried` is the `Currency Usage Counter.lastQueriedDate` field, i.e. the most recent date
  on which that currency was involved in a successful `/api/exchange` lookup.

**Errors**: None expected under normal operation — an empty `topCurrencies: []` array is returned
(not a `404`) when no lookups have ever occurred, since "no usage yet" is a valid, expected state
for a freshly-seeded system, not an error condition.

**Behavioral note**: This endpoint is read-only and has no side effects; it MUST NOT be callable
in a way that mutates any counter (i.e., viewing analytics never counts as a "query" for
usage-tracking purposes — only `/api/exchange` lookups do, per FR-011's scope).
