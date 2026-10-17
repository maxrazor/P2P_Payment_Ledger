package com.dfls.account_service.config;

import tools.jackson.databind.json.JsonMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestJacksonConfiguration {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        
        return properties -> properties.put(
            AvailableSettings.JSON_FORMAT_MAPPER, 
            new Jackson3JsonFormatMapper(jsonMapper)
        );
    }
}