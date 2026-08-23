/** `GET /api/exchange` success response — contracts/exchange.md. */
export interface ExchangeRateResponse {
  from: string;
  to: string;
  exchange: number;
  date: string;
  fromQueryCount: number;
  toQueryCount: number;
}
