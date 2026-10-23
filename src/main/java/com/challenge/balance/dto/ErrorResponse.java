package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Standard error response representation")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Error categorization", example = "Invalid Amount")
        String error,

        @Schema(description = "Detailed error description message", example = "Invalid transaction amount: -50. Amount must be strictly positive (> 0).")
        String message,

        @Schema(description = "Timestamp when error occurred")
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
