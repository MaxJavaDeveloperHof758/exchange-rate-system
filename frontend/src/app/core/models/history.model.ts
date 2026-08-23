/** One entry in `HistoryResponse.points` — contracts/exchange.md. */
export interface HistoricalRatePoint {
  date: string;
  exchange: number;
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
