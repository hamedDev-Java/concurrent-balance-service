package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for crediting an account balance")
public record CreditRequest(
        @Schema(description = "Amount to credit in lowest monetary units (strictly > 0)", example = "500")
        @Positive(message = "amount must be strictly positive")
        long amount,

        @Schema(description = "Globally unique transaction identifier for idempotency", example = "TX-CRED-1001")
        @NotBlank(message = "transactionId cannot be blank")
        String transactionId
) {}
