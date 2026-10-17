package com.dfls.transfer_service.integration;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean; 
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.dfls.transfer_service.client.AccountClient;
import com.dfls.transfer_service.config.TestJacksonConfiguration;

import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.concurrent.CompletableFuture;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "spring.kafka.admin.auto-create=false", 
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:0",

        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yml"
    }
)
@Testcontainers 
@Import(TestJacksonConfiguration.class)
@AutoConfigureMockMvc 
public abstract class BaseIntegrationTest {

    @Value("${gateway.secret}")
    protected String GATEWAY_SECRET_VALUE;

    protected static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected AccountClient accountClient;

    @MockitoBean
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setupKafkaMocking() {
        SendResult<String, Object> mockResult = Mockito.mock(SendResult.class);
    
        CompletableFuture<SendResult<String, Object>> completedFuture = 
                CompletableFuture.completedFuture(mockResult);

        Mockito.when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(completedFuture);
    }

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
}