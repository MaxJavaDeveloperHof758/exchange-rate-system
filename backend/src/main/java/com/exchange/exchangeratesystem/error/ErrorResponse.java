package com.exchange.exchangeratesystem.error;

import io.swagger.v3.oas.annotations.media.Schema;

/** The one error body shape used across every endpoint, per contracts/*.md. */
public record ErrorResponse(
        @Schema(
                description = "Machine-readable error code.",
                example = "UNKNOWN_CURRENCY")
                String error,
        @Schema(
                description = "Human-readable detail for this specific occurrence.",
                example = "Unknown currency code: XXX")
                String message) {
}
