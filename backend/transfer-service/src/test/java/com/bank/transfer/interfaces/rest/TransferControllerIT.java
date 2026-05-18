package com.bank.transfer.interfaces.rest;

import com.bank.transfer.infrastructure.persistence.IdempotencyJpaRepository;
import com.bank.transfer.infrastructure.persistence.OutboxJpaRepository;
import com.bank.transfer.infrastructure.persistence.TransferJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TransferController} using a real PostgreSQL container.
 *
 * <p>Spring context is fully loaded ({@code @SpringBootTest}) against a Testcontainers
 * PostgreSQL instance. Flyway runs all migrations before tests execute.
 * The stub {@link com.bank.transfer.infrastructure.client.AccountClientStub} is used
 * in place of the real account-service (returns ACTIVE account, 10M THB).
 *
 * <p>If Docker is not available in the execution environment, the test class will fail
 * to start the container and tests will be skipped/errored. The handoff artifact
 * notes this as a known limitation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("TransferController integration tests")
class TransferControllerIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transfer_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferJpaRepository transferJpaRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private IdempotencyJpaRepository idempotencyJpaRepository;

    @BeforeEach
    void cleanDb() {
        // Clean in dependency order: outbox + idempotency reference transfers, transfers last
        outboxJpaRepository.deleteAll();
        idempotencyJpaRepository.deleteAll();
        transferJpaRepository.deleteAll();
    }

    // --- Happy path ---

    @Test
    @WithMockUser
    @DisplayName("shouldCreateTransfer_whenSufficientBalance_andValidIdempotencyKey")
    void shouldCreateTransfer_whenSufficientBalance_andValidIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = validTransferRequest();

        MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.transferId").isNotEmpty())
            .andExpect(jsonPath("$.referenceNumber").value(
                org.hamcrest.Matchers.startsWith("TRF-")))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.amount").value("1500.0000"))
            .andExpect(jsonPath("$.currency").value("THB"))
            .andExpect(jsonPath("$.idempotencyStatus").value("FIRST_WRITE"))
            .andReturn();

        // Verify DB has the transfer row
        assertThat(transferJpaRepository.count()).isEqualTo(1L);

        // Verify outbox has at least one entry
        assertThat(outboxJpaRepository.count()).isGreaterThanOrEqualTo(1L);

        // Verify transferId in response matches DB
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        UUID returnedId = UUID.fromString(json.get("transferId").asText());
        assertThat(transferJpaRepository.findById(returnedId)).isPresent();
    }

    // --- Idempotency replay ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturnCachedResponse_whenIdempotencyKeyReplayed")
    void shouldReturnCachedResponse_whenIdempotencyKeyReplayed() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = validTransferRequest();

        // First call
        MvcResult firstResult = mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotencyStatus").value("FIRST_WRITE"))
            .andReturn();

        String firstBody = firstResult.getResponse().getContentAsString();
        JsonNode firstJson = objectMapper.readTree(firstBody);
        String firstTransferId = firstJson.get("transferId").asText();

        // Second call — same key, same payload
        MvcResult secondResult = mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotencyStatus").value("IDEMPOTENT_REPLAY"))
            .andReturn();

        String secondBody = secondResult.getResponse().getContentAsString();
        JsonNode secondJson = objectMapper.readTree(secondBody);

        // transferId and referenceNumber must be identical across both calls
        assertThat(secondJson.get("transferId").asText()).isEqualTo(firstTransferId);
        assertThat(secondJson.get("referenceNumber").asText())
            .isEqualTo(firstJson.get("referenceNumber").asText());

        // Only one transfer row in DB
        assertThat(transferJpaRepository.count()).isEqualTo(1L);
    }

    // --- Idempotency conflict (409) ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturn409_whenIdempotencyKeyReusedWithDifferentPayload")
    void shouldReturn409_whenIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // First call with original payload
        mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferRequest()))
            .andExpect(status().isOk());

        // Second call with DIFFERENT amount — same idempotency key
        String differentAmountRequest = """
            {
              "sourceAccountId": "11111111-1111-1111-1111-111111111111",
              "destinationAccountId": "22222222-2222-2222-2222-222222222222",
              "amount": "9999.0000",
              "currency": "THB",
              "memo": "Different amount",
              "channel": "INTERNET_BANKING"
            }
            """;

        mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(differentAmountRequest))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"))
            .andExpect(jsonPath("$.status").value(409));
    }

    // --- Missing Idempotency-Key header (400) ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturn400_whenIdempotencyKeyHeaderMissing")
    void shouldReturn400_whenIdempotencyKeyHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Get transfer (200) ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturn200WithTransferStatus_whenGetTransferByValidId")
    void shouldReturn200WithTransferStatus_whenGetTransferByValidId() throws Exception {
        // Create a transfer first
        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult createResult = mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferRequest()))
            .andExpect(status().isOk())
            .andReturn();

        String transferId = objectMapper
            .readTree(createResult.getResponse().getContentAsString())
            .get("transferId").asText();

        // GET the transfer
        mockMvc.perform(get("/api/v1/transfers/{transferId}", transferId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transferId").value(transferId))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // --- Get transfer (404) ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturn404_whenTransferIdDoesNotExist")
    void shouldReturn404_whenTransferIdDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/{transferId}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    // --- Validation: missing required field (400) ---

    @Test
    @WithMockUser
    @DisplayName("shouldReturn400_whenRequiredFieldMissing")
    void shouldReturn400_whenRequiredFieldMissing() throws Exception {
        String missingAmount = """
            {
              "sourceAccountId": "11111111-1111-1111-1111-111111111111",
              "destinationAccountId": "22222222-2222-2222-2222-222222222222",
              "currency": "THB"
            }
            """;

        mockMvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingAmount))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Helper ---

    private String validTransferRequest() {
        return """
            {
              "sourceAccountId": "11111111-1111-1111-1111-111111111111",
              "destinationAccountId": "22222222-2222-2222-2222-222222222222",
              "amount": "1500.0000",
              "currency": "THB",
              "memo": "Rent payment May 2026",
              "channel": "INTERNET_BANKING"
            }
            """;
    }
}
