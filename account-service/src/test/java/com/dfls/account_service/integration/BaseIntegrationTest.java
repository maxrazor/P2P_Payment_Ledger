package com.dfls.account_service.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.dfls.account_service.config.TestJacksonConfiguration;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.scheduling.enable=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.consumer.group-id=dummy-test-group",
        "spring.kafka.properties.max.block.ms=100",
        "spring.kafka.properties.request.timeout.ms=100",
        
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",

        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yml"
    }
)
@EnableAutoConfiguration(excludeName = {
    "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration" 
})
@Testcontainers
@Import(TestJacksonConfiguration.class) 
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    
}
