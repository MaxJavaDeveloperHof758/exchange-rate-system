/**
 * One entry in `HistoryResponse.points` — contracts/exchange.md.
 * `fromRateToUsd`/`toRateToUsd` are each currency's own raw stored
 * rate-to-USD for this date (`null` for a same-currency pair, which has no
 * lookup at all); `adjustedRate` is the derived spread-adjusted pair rate.
 */
export interface HistoricalRatePoint {
  date: string;
  fromRateToUsd: number | null;
  toRateToUsd: number | null;
  adjustedRate: number;
}

/** `GET /api/exchange/history` success response — contracts/exchange.md. */
export interface HistoryResponse {
  from: string;
  to: string;
  startDate: string;
  endDate: string;
  points: HistoricalRatePoint[];
  missingDates: string[];
}
