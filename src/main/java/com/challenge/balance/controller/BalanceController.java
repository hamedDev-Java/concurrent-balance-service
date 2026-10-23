package com.challenge.balance.controller;

import com.challenge.balance.dto.BalanceResponse;
import com.challenge.balance.dto.CreateAccountRequest;
import com.challenge.balance.dto.CreditRequest;
import com.challenge.balance.dto.DebitRequest;
import com.challenge.balance.dto.ErrorResponse;
import com.challenge.balance.dto.TransactionResponse;
import com.challenge.balance.dto.TransferRequest;
import com.challenge.balance.service.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Balance Operations", description = "Endpoints for managing account balances, credits, debits, transfers, and transaction history")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Operation(summary = "Create new account", description = "Registers a new user account with an initial non-negative balance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or negative balance",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Account with this ID already exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public void createAccount(@Valid @RequestBody CreateAccountRequest request) {
        balanceService.createAccount(request.accountId(), request.initialBalance());
    }

    @Operation(summary = "Get account balance", description = "Retrieves the current balance for the given account ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Unique account ID", example = "ACC-A", required = true)
            @PathVariable String accountId) {
        long balance = balanceService.getBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(accountId, balance));
    }

    @Operation(summary = "Get transaction history (Paged)", description = "Retrieves paged credit, debit, and transfer transactions involving the given account ID, ordered newest first by default.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction history page retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @Parameter(description = "Unique account ID", example = "ACC-A", required = true)
            @PathVariable String accountId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TransactionResponse> history = balanceService.getTransactionHistory(accountId, pageable);
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Credit account", description = "Increases the account balance by the specified amount. Idempotent based on transactionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Credit applied or replayed idempotently"),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid amount",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict: transactionId reused with different parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/accounts/{accountId}/credit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void credit(
            @Parameter(description = "Target account ID", example = "ACC-A", required = true)
            @PathVariable String accountId,
            @Valid @RequestBody CreditRequest request) {
        balanceService.credit(accountId, request.amount(), request.transactionId());
    }

    @Operation(summary = "Debit account", description = "Decreases the account balance by the specified amount if sufficient funds exist. Idempotent based on transactionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Debit applied or replayed idempotently"),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid amount",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict: transactionId reused with different parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds in the account",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/accounts/{accountId}/debit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void debit(
            @Parameter(description = "Source account ID", example = "ACC-A", required = true)
            @PathVariable String accountId,
            @Valid @RequestBody DebitRequest request) {
        balanceService.debit(accountId, request.amount(), request.transactionId());
    }

    @Operation(summary = "Atomic balance transfer", description = "Transfers an amount from source account to destination account atomically without deadlocks. Idempotent based on transactionId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Transfer completed or replayed idempotently"),
            @ApiResponse(responseCode = "400", description = "Validation error, invalid amount, or self-transfer attempt",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Source or destination account not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict: transactionId reused with different parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds in source account",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@Valid @RequestBody TransferRequest request) {
        balanceService.transfer(
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.transactionId()
        );
    }
}
