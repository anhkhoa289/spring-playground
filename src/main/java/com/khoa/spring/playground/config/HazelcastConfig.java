package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "hazelcast")
public class HazelcastConfig {

	private final Environment environment;

	public HazelcastConfig(Environment environment) {
		this.environment = environment;
	}

	@Bean
	public Config hazelcastConfiguration() throws IOException {
		String configFile = getHazelcastConfigFile();
		log.info("Loading Hazelcast configuration from: {}", configFile);

		ClassPathResource resource = new ClassPathResource(configFile);
		try (InputStream inputStream = resource.getInputStream()) {
			return new YamlConfigBuilder(inputStream).build();
		}
	}

	@Bean
	public HazelcastInstance hazelcastInstance(Config hazelcastConfiguration) {
		return Hazelcast.newHazelcastInstance(hazelcastConfiguration);
	}

	/**
	 * Determines which Hazelcast configuration file to load based on active Spring profiles.
	 * - k8s profile: hazelcast-k8s.yml (Kubernetes discovery)
	 * - default: hazelcast.yml (TCP-IP for local development)
	 */
	private String getHazelcastConfigFile() {
		String[] activeProfiles = environment.getActiveProfiles();
		log.debug("Active Spring profiles: {}", Arrays.toString(activeProfiles));

		if (Arrays.asList(activeProfiles).contains("k8s")) {
			return "hazelcast-k8s.yml";
		}
		return "hazelcast.yml";
	}

}
