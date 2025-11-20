package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class HazelcastConfig {

    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setInstanceName("hazelcast-instance");

        // Network configuration
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(5701);
        networkConfig.setPortAutoIncrement(true);

        // Join configuration
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig()
            .setEnabled(true)
            .addMember("localhost")
            .addMember("hazelcast");

        // Idempotency cache configuration
        MapConfig idempotencyMapConfig = new MapConfig("idempotency-cache");
        idempotencyMapConfig.setTimeToLiveSeconds((int) TimeUnit.HOURS.toSeconds(24)); // 24 hours TTL
        idempotencyMapConfig.setMaxIdleSeconds((int) TimeUnit.HOURS.toSeconds(1)); // Remove if idle for 1 hour
        config.addMapConfig(idempotencyMapConfig);

        return config;
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfig) {
        return Hazelcast.newHazelcastInstance(hazelcastConfig);
    }
}
