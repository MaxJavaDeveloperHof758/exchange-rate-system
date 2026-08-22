package com.exchange.exchangeratesystem.error;

/** The one error body shape used across every endpoint, per contracts/*.md. */
public record ErrorResponse(String error, String message) {
}
