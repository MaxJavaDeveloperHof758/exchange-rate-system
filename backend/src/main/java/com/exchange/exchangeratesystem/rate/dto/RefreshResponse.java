package com.exchange.exchangeratesystem.rate.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/exchange/refresh} success response, per contracts/exchange.md. */
public record RefreshResponse(
        @Schema(description = "When this manual ingestion run was triggered.",
                example = "2026-08-21T10:15:00Z")
                Instant triggeredAt,
        @Schema(description = "Always \"COMPLETED\" on a 202 response.", example = "COMPLETED")
                String status) {
}
