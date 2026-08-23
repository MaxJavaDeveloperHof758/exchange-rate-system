/** One entry in `AnalyticsResponse.topCurrencies` — contracts/analytics.md. */
export interface CurrencyUsageEntry {
  currency: string;
  totalCount: number;
  lastQueried: string;
}

/** `GET /api/analytics` success response — contracts/analytics.md. */
export interface AnalyticsResponse {
  topCurrencies: CurrencyUsageEntry[];
}
