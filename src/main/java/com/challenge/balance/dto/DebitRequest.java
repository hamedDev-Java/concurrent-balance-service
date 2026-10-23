package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for debiting an account balance")
public record DebitRequest(
        @Schema(description = "Amount to debit in lowest monetary units (strictly > 0)", example = "300")
        @Positive(message = "amount must be strictly positive")
        long amount,

        @Schema(description = "Globally unique transaction identifier for idempotency", example = "TX-DEB-2001")
        @NotBlank(message = "transactionId cannot be blank")
        String transactionId
) {}
