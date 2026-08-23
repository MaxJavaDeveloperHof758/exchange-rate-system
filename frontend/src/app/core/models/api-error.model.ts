/** The one error body shape used across every backend endpoint — error/ErrorResponse.java. */
export interface ApiErrorResponse {
  error: string;
  message: string;
}
