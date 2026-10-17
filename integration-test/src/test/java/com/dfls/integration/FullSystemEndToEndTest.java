package com.dfls.integration;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.account_service.ledger.LedgerService;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.model.TransactionStatus;
import com.dfls.transfer_service.repository.TransactionRepository;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Slf4j 
public class FullSystemEndToEndTest {

    @SuppressWarnings("resource")
    static PostgreSQLContainer accountPostgres = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("dfls_account")
        .withUsername("test_user")
        .withPassword("test_pass");

    @SuppressWarnings("resource")
    static PostgreSQLContainer transferPostgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("dfls_transfer")
            .withUsername("test_user")
            .withPassword("test_pass");

    private static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    private static ConfigurableApplicationContext accountServiceContext;
    private static ConfigurableApplicationContext transferServiceContext;

    private static final int ACCOUNT_PORT = 8086;
    private static final int TRANSFER_PORT = 8085;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GATEWAY_SECRET = "$2a$12$rCmDdT9GlGwITpiElVRaPu7UR8rvtV2YcdZg/eOZwJ1mqDm9Bci3m";
    private static final String MESH_TOKEN = "8c284055dbb54b7f053a2dc612c3727c7aa36354361055f2110f4903ea8ee29c";

    @BeforeAll
    static void startDistributedClusterMesh() throws Exception {
        accountPostgres.start();
        transferPostgres.start();
        kafka.start();

        try (Connection conn = DriverManager.getConnection(
                accountPostgres.getJdbcUrl(), accountPostgres.getUsername(), accountPostgres.getPassword())) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            
            java.io.File targetDir = Paths.get("target/test-classes").toFile();
            
            try (Liquibase liquibase = new Liquibase("db/changelog/test-account-master.yml", new DirectoryResourceAccessor(targetDir), db)) {
                liquibase.update("");
                log.info("Successfully migrated Account Database!");
            }
        }

        try (Connection conn = DriverManager.getConnection(
                transferPostgres.getJdbcUrl(), transferPostgres.getUsername(), transferPostgres.getPassword())) {
            Database db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            
            java.io.File targetDir = Paths.get("target/test-classes").toFile();
            
            try (Liquibase liquibase = new Liquibase("db/changelog/test-transfer-master.yml", new DirectoryResourceAccessor(targetDir), db)) {
                liquibase.update("");
                log.info("Successfully migrated Transfer Database!");
            }
        }

        System.setProperty("spring.kafka.bootstrap-servers", kafka.getBootstrapServers());

        String[] accountArgs = new String[]{
            "--server.port=" + ACCOUNT_PORT,
            "--spring.datasource.url=" + accountPostgres.getJdbcUrl(),
            "--spring.datasource.username=" + accountPostgres.getUsername(),
            "--spring.datasource.password=" + accountPostgres.getPassword(),
            "--spring.liquibase.enabled=true",
            "--spring.liquibase.change-log=classpath:db/changelog/test-account-master.yml",
            "--spring.jpa.hibernate.ddl-auto=none"
        };

        accountServiceContext = new SpringApplicationBuilder(com.dfls.account_service.AccountServiceApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + accountPostgres.getJdbcUrl(),
                        "spring.datasource.username=" + accountPostgres.getUsername(),
                        "spring.datasource.password=" + accountPostgres.getPassword(),
                        "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "app.scheduling.enable=true",
                        "app.security.internal-secret-token=" + MESH_TOKEN,
                        "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3"
                ).run(accountArgs);

        String[] transferArgs = new String[]{
            "--server.port=" + TRANSFER_PORT,
            "--spring.datasource.url=" + transferPostgres.getJdbcUrl(),
            "--spring.datasource.username=" + transferPostgres.getUsername(),
            "--spring.datasource.password=" + transferPostgres.getPassword(),
            "--spring.liquibase.enabled=true",
            "--spring.liquibase.change-log=classpath:db/changelog/test-transfer-master.yml",
            "--spring.jpa.hibernate.ddl-auto=none"
        };

        SpringApplicationBuilder transferBuilder  = new SpringApplicationBuilder(com.dfls.transfer_service.TransferServiceApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + transferPostgres.getJdbcUrl(),
                        "spring.datasource.username=" + transferPostgres.getUsername(),
                        "spring.datasource.password=" + transferPostgres.getPassword(),
                        "spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                        "app.scheduling.enable=true",
                        "services.account-service.url=http://localhost:" + ACCOUNT_PORT,
                        "gateway.secret=" + GATEWAY_SECRET,
                        "app.security.internal-secret-token=" + MESH_TOKEN,
                        "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3"
                        );

        transferBuilder .initializers(context -> TestPropertyValues.of(
                "services.account-service.url=http://localhost:" + ACCOUNT_PORT
        ).applyTo(context.getEnvironment()));

        transferServiceContext = transferBuilder.run(transferArgs);
    }

    @Test
    void shouldExecuteFullCrossServiceDistributedTransactionPipelineSuccessfully() throws Exception {
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        String senderEmail = "sender.system.e2e@mail.com";
        String receiverEmail = "receiver.system.e2e@mail.com";
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Secret", GATEWAY_SECRET);

        String createAccountPath = "http://localhost:" + ACCOUNT_PORT + "/api/v1/accounts";
        
        headers.set("X-Authenticated-User-Id", senderUserId.toString());
        restTemplate.postForEntity(createAccountPath, new HttpEntity<>(String.format("{\"currency\":\"USD\",\"email\":\"%s\"}", senderEmail), headers), Void.class);
        
        headers.set("X-Authenticated-User-Id", receiverUserId.toString());
        restTemplate.postForEntity(createAccountPath, new HttpEntity<>(String.format("{\"currency\":\"USD\",\"email\":\"%s\"}", receiverEmail), headers), Void.class);

        AccountRepository accountRepository = accountServiceContext.getBean(AccountRepository.class);
        LedgerRepository ledgerRepository = accountServiceContext.getBean(LedgerRepository.class);
        TransactionRepository transactionRepository = transferServiceContext.getBean(TransactionRepository.class);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertFalse(accountRepository.findByUserId(senderUserId).isEmpty());
            assertFalse(accountRepository.findByUserId(receiverUserId).isEmpty());
        });

        Account senderAccount = accountRepository.findByUserId(senderUserId).get(0);
        Account receiverAccount = accountRepository.findByUserId(receiverUserId).get(0);

        LedgerService ledgerService = accountServiceContext.getBean(LedgerService.class);
        ledgerService.createCredit(senderAccount.getId(), UUID.randomUUID(), new BigDecimal("200.00"), "USD", "E2E Pre-Funding");
        
        assertEquals(0, ledgerService.getUserBalance(senderUserId, senderAccount.getId()).compareTo(new BigDecimal("200.00")));

        String transferInboundPath = "http://localhost:" + TRANSFER_PORT + "/api/v1/transfers";
        
        String transferPayload = String.format("""
            {
                "sourceAccountId": "%s",
                "destinationEmail": "%s",
                "amount": 75.00,
                "currency": "USD"
            }
            """, senderAccount.getId(), receiverEmail);

        headers.set("X-Authenticated-User-Id", senderUserId.toString());
        headers.set("X-Idempotency-Key", idempotencyKey);
        
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(transferInboundPath, new HttpEntity<>(transferPayload, headers), Void.class);
        assertEquals(HttpStatus.CREATED, transferResponse.getStatusCode());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long ledgerEntriesCount = ledgerRepository.count();
            assertEquals(3, ledgerEntriesCount, "Kafka consumers must asynchronously populate debit and credit entries");
            
            BigDecimal senderBalance = ledgerService.getUserBalance(senderUserId, senderAccount.getId());
            BigDecimal receiverBalance = ledgerService.getUserBalance(receiverUserId, receiverAccount.getId());

            assertEquals(0, senderBalance.compareTo(new BigDecimal("125.00")), "Sender should be deducted ($200 - $75)");
            assertEquals(0, receiverBalance.compareTo(new BigDecimal("75.00")), "Receiver should be credited ($0 + $75)");
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Transaction> transactions = transactionRepository.findAll();
            Transaction dbTx = transactions.stream()
                    .filter(t -> idempotencyKey.equals(t.getIdempotencyKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Transaction tracking record missing"));

            assertEquals(TransactionStatus.COMPLETED, dbTx.getStatus(), 
            "The async completion result loop must close and transition the transaction status to COMPLETED");
        });
    }

    @AfterAll
    static void tearDownDistributedClusterMesh() {
        if (transferServiceContext != null) transferServiceContext.close();
        if (accountServiceContext != null) accountServiceContext.close();
        accountPostgres.stop();
        transferPostgres.stop();
        kafka.start();
    }
}