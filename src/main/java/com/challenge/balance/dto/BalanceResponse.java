package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Account balance response")
public record BalanceResponse(
        @Schema(description = "Account identifier", example = "ACC-A")
        String accountId,

        @Schema(description = "Current balance in lowest monetary units", example = "1500")
        long balance
) {}
