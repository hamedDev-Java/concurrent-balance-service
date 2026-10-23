package com.challenge.balance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for creating a new account")
public record CreateAccountRequest(
        @Schema(description = "Unique account identifier", example = "ACC-100")
        @NotBlank(message = "accountId cannot be blank")
        String accountId,

        @Schema(description = "Initial balance in lowest monetary units (>= 0)", example = "1000")
        @Min(value = 0, message = "initialBalance cannot be negative")
        long initialBalance
) {}
