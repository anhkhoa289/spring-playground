package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "hazelcast")
public class HazelcastConfig {

    @Bean
    public Config hazelcastConfiguration() throws IOException {
        ClassPathResource resource = new ClassPathResource("hazelcast.yml");
        try (InputStream inputStream = resource.getInputStream()) {
            return new YamlConfigBuilder(inputStream).build();
        }
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfiguration) {
        return Hazelcast.newHazelcastInstance(hazelcastConfiguration);
    }
}
