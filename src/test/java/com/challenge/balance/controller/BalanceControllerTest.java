package com.challenge.balance.controller;

import com.challenge.balance.domain.OperationType;
import com.challenge.balance.dto.CreateAccountRequest;
import com.challenge.balance.dto.CreditRequest;
import com.challenge.balance.dto.DebitRequest;
import com.challenge.balance.dto.TransactionResponse;
import com.challenge.balance.dto.TransferRequest;
import com.challenge.balance.exception.AccountAlreadyExistsException;
import com.challenge.balance.exception.AccountNotFoundException;
import com.challenge.balance.exception.IdempotencyConflictException;
import com.challenge.balance.exception.InsufficientFundsException;
import com.challenge.balance.exception.SameAccountTransferException;
import com.challenge.balance.service.BalanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BalanceService balanceService;

    @Test
    @DisplayName("POST /api/v1/accounts - 201 CREATED")
    void createAccount_success() throws Exception {
        CreateAccountRequest req = new CreateAccountRequest("ACC-NEW", 1000L);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(balanceService).createAccount("ACC-NEW", 1000L);
    }

    @Test
    @DisplayName("POST /api/v1/accounts - 409 CONFLICT on existing account")
    void createAccount_alreadyExists() throws Exception {
        CreateAccountRequest req = new CreateAccountRequest("ACC-EXISTS", 500L);
        doThrow(new AccountAlreadyExistsException("ACC-EXISTS"))
                .when(balanceService).createAccount("ACC-EXISTS", 500L);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Account Already Exists"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId}/balance - 200 OK")
    void getBalance_success() throws Exception {
        when(balanceService.getBalance("ACC-100")).thenReturn(5000L);

        mockMvc.perform(get("/api/v1/accounts/ACC-100/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-100"))
                .andExpect(jsonPath("$.balance").value(5000L));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId}/balance - 404 NOT FOUND")
    void getBalance_notFound() throws Exception {
        when(balanceService.getBalance("UNKNOWN")).thenThrow(new AccountNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/accounts/UNKNOWN/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId}/transactions - 200 OK with Paging")
    void getTransactionHistory_success() throws Exception {
        TransactionResponse tx1 = new TransactionResponse("TX-1", OperationType.CREDIT, null, "ACC-A", 1000L, Instant.now());
        when(balanceService.getTransactionHistory(eq("ACC-A"), any()))
                .thenReturn(new PageImpl<>(List.of(tx1), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/accounts/ACC-A/transactions?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value("TX-1"))
                .andExpect(jsonPath("$.content[0].operationType").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].amount").value(1000L))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{accountId}/credit - 204 NO CONTENT")
    void credit_success() throws Exception {
        CreditRequest req = new CreditRequest(500L, "TX-CRED-1");

        mockMvc.perform(post("/api/v1/accounts/ACC-100/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(balanceService).credit("ACC-100", 500L, "TX-CRED-1");
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{accountId}/credit - 400 BAD REQUEST on invalid amount")
    void credit_invalidAmount() throws Exception {
        CreditRequest req = new CreditRequest(-100L, "TX-CRED-2");

        mockMvc.perform(post("/api/v1/accounts/ACC-100/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{accountId}/debit - 422 UNPROCESSABLE_ENTITY on insufficient funds")
    void debit_insufficientFunds() throws Exception {
        DebitRequest req = new DebitRequest(2000L, "TX-DEB-1");
        doThrow(new InsufficientFundsException("ACC-100", 1000L, 2000L))
                .when(balanceService).debit("ACC-100", 2000L, "TX-DEB-1");

        mockMvc.perform(post("/api/v1/accounts/ACC-100/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"));
    }

    @Test
    @DisplayName("POST /api/v1/transfers - 204 NO CONTENT")
    void transfer_success() throws Exception {
        TransferRequest req = new TransferRequest("ACC-A", "ACC-B", 300L, "TX-TR-1");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(balanceService).transfer("ACC-A", "ACC-B", 300L, "TX-TR-1");
    }

    @Test
    @DisplayName("POST /api/v1/transfers - 400 BAD REQUEST on self transfer")
    void transfer_sameAccount() throws Exception {
        TransferRequest req = new TransferRequest("ACC-A", "ACC-A", 100L, "TX-TR-2");
        doThrow(new SameAccountTransferException("ACC-A"))
                .when(balanceService).transfer("ACC-A", "ACC-A", 100L, "TX-TR-2");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Transfer"));
    }

    @Test
    @DisplayName("POST /api/v1/transfers - 409 CONFLICT on idempotency conflict")
    void transfer_idempotencyConflict() throws Exception {
        TransferRequest req = new TransferRequest("ACC-A", "ACC-B", 500L, "TX-TR-3");
        doThrow(new IdempotencyConflictException("TX-TR-3"))
                .when(balanceService).transfer("ACC-A", "ACC-B", 500L, "TX-TR-3");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Idempotency Conflict"));
    }
}
