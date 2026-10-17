package com.dfls.identity_service.integration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.dfls.identity_service.config.TestJacksonConfiguration;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.scheduling.enable=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "spring.kafka.admin.auto-create=false", 
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:0",

        "spring.kafka.properties.reconnect.backoff.ms=9999999",
        "spring.kafka.properties.reconnect.backoff.max.ms=9999999",
        "spring.kafka.properties.retry.backoff.ms=9999999",
        "spring.kafka.producer.retries=0",

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

    @MockitoBean
    private ProducerFactory<Object, Object> producerFactory;

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @SuppressWarnings("unchecked") 
    @BeforeEach
    void setupKafkaMocking() {
        Producer<Object, Object> mockProducer = Mockito.mock(Producer.class);
        
        RecordMetadata mockMetadata = Mockito.mock(RecordMetadata.class);
        
        Future<RecordMetadata> completedFuture = CompletableFuture.completedFuture(mockMetadata);
        
        Mockito.when(mockProducer.send(Mockito.any())).thenReturn(completedFuture);
        Mockito.when(mockProducer.send(Mockito.any(), Mockito.any())).thenReturn(completedFuture);

        Mockito.when(producerFactory.createProducer(Mockito.any())).thenReturn(mockProducer);
        Mockito.when(producerFactory.createProducer()).thenReturn(mockProducer);
    }

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    
}
