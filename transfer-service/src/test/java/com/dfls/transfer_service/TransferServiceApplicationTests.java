package com.dfls.transfer_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        // Disables background Kafka listeners from booting up during this simple context smoke test
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.consumer.group-id=smoke-test-group",
        "spring.kafka.bootstrap-servers=localhost:0",
        
        // Satisfies your RestClient URL boundary property and gateway secret requirements
        "services.account-service.url=http://localhost:8083",
        "gateway.secret=$2a$12$rCmDdT9GlGwITpiElVRaPu7UR8rvtV2YcdZg/eOZwJ1mqDm9Bci3m",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yml"
    }
)
class TransferServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
