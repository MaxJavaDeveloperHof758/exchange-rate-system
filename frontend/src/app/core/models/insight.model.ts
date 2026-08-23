/** `GET /api/exchange/insight` success response — contracts/insight.md. */
export interface InsightResponse {
  from: string;
  to: string;
  fromDate: string;
  toDate: string;
  insight: string;
}
