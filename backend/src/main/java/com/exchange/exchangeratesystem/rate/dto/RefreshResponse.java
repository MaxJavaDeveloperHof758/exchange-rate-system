package com.exchange.exchangeratesystem.rate.dto;

import java.time.Instant;

/** {@code POST /api/exchange/refresh} success response, per contracts/exchange.md. */
public record RefreshResponse(Instant triggeredAt, String status) {
}
