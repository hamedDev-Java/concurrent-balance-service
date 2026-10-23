package com.challenge.balance.dto;

import com.challenge.balance.domain.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Transaction record summary")
public record TransactionResponse(
        @Schema(description = "Globally unique transaction identifier", example = "TX-CRED-1001")
        String transactionId,

        @Schema(description = "Operation type", example = "CREDIT")
        OperationType operationType,

        @Schema(description = "Source account ID (null for credit operations)", example = "ACC-A")
        String sourceAccountId,

        @Schema(description = "Destination account ID (null for debit operations)", example = "ACC-B")
        String destinationAccountId,

        @Schema(description = "Amount in lowest monetary units", example = "500")
        long amount,

        @Schema(description = "Timestamp when transaction was completed")
        Instant createdAt
) {}
