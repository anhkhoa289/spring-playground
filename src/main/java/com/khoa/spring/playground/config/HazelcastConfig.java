package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "hazelcast", matchIfMissing = false)
public class HazelcastConfig {

    @Bean
    public Config hazelcastConfiguration() {
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

        return config;
    }

    @Bean
    public HazelcastInstance hazelcastInstance(Config hazelcastConfiguration) {
        return Hazelcast.newHazelcastInstance(hazelcastConfiguration);
    }
}
