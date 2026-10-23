package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request payload for atomic balance transfer between two accounts")
public record TransferRequest(
        @Schema(description = "Identifier of the source account", example = "ACC-A")
        @NotBlank(message = "sourceAccountId cannot be blank")
        String sourceAccountId,

        @Schema(description = "Identifier of the destination account", example = "ACC-B")
        @NotBlank(message = "destinationAccountId cannot be blank")
        String destinationAccountId,

        @Schema(description = "Amount to transfer in lowest monetary units (strictly > 0)", example = "250")
        @Positive(message = "amount must be strictly positive")
        long amount,

        @Schema(description = "Globally unique transaction identifier for idempotency", example = "TX-TR-3001")
        @NotBlank(message = "transactionId cannot be blank")
        String transactionId
) {}
