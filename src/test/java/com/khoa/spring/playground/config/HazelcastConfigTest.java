package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HazelcastConfigTest {

    private HazelcastConfig hazelcastConfig;
    private HazelcastInstance hazelcastInstance;

    @BeforeEach
    void setUp() {
        // Shutdown all existing instances to avoid conflicts
        Hazelcast.shutdownAll();
        hazelcastConfig = new HazelcastConfig();
    }

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
            hazelcastInstance.shutdown();
        }
        // Ensure clean state after each test
        Hazelcast.shutdownAll();
    }

    @Test
    void hazelcastConfig_ShouldCreateConfigWithCorrectInstanceName() {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();

        // Assert
        assertNotNull(config);
        assertEquals("hazelcast-instance", config.getInstanceName());
    }

    @Test
    void hazelcastConfig_ShouldConfigureNetworkCorrectly() {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        NetworkConfig networkConfig = config.getNetworkConfig();

        // Assert
        assertNotNull(networkConfig);
        assertEquals(5701, networkConfig.getPort());
        assertTrue(networkConfig.isPortAutoIncrement());
    }

    @Test
    void hazelcastConfig_ShouldDisableMulticast() {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();

        // Assert
        assertNotNull(joinConfig);
        assertFalse(joinConfig.getMulticastConfig().isEnabled());
    }

    @Test
    void hazelcastConfig_ShouldEnableTcpIpWithMembers() {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();

        // Assert
        assertNotNull(joinConfig);
        assertTrue(joinConfig.getTcpIpConfig().isEnabled());
        assertTrue(joinConfig.getTcpIpConfig().getMembers().contains("localhost"));
        assertTrue(joinConfig.getTcpIpConfig().getMembers().contains("hazelcast"));
        assertEquals(2, joinConfig.getTcpIpConfig().getMembers().size());
    }

    @Test
    void hazelcastInstance_ShouldCreateInstanceWithConfig() {
        // Arrange
        Config config = hazelcastConfig.hazelcastConfiguration();
        // Disable network join for unit test to avoid cluster connection issues
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

        // Act
        hazelcastInstance = hazelcastConfig.hazelcastInstance(config);

        // Assert
        assertNotNull(hazelcastInstance);
        assertEquals("hazelcast-instance", hazelcastInstance.getName());
        assertTrue(hazelcastInstance.getLifecycleService().isRunning());
    }

    @Test
    void hazelcastInstance_ShouldUseProvidedConfig() {
        // Arrange
        Config config = hazelcastConfig.hazelcastConfiguration();
        // Disable network join for unit test to avoid cluster connection issues
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

        // Act
        hazelcastInstance = hazelcastConfig.hazelcastInstance(config);

        // Assert
        assertNotNull(hazelcastInstance);
        assertNotNull(hazelcastInstance.getConfig());
        assertEquals(config.getInstanceName(), hazelcastInstance.getConfig().getInstanceName());
    }

    @Test
    void hazelcastConfig_ShouldBeReusable() {
        // Act
        Config config1 = hazelcastConfig.hazelcastConfiguration();
        Config config2 = hazelcastConfig.hazelcastConfiguration();

        // Assert
        assertNotNull(config1);
        assertNotNull(config2);
        // Each call creates a new instance
        assertNotSame(config1, config2);
        // But with same configuration
        assertEquals(config1.getInstanceName(), config2.getInstanceName());
        assertEquals(config1.getNetworkConfig().getPort(), config2.getNetworkConfig().getPort());
    }
}
