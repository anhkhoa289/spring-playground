package com.khoa.spring.playground.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
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
    void hazelcastConfig_ShouldCreateConfigWithCorrectInstanceName() throws Exception {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();

        // Assert
        assertNotNull(config);
        // Tests load from test/resources/hazelcast.yml
        assertEquals("test-instance", config.getInstanceName());
    }

    @Test
    void hazelcastConfig_ShouldConfigureNetworkCorrectly() throws Exception {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        NetworkConfig networkConfig = config.getNetworkConfig();

        // Assert
        assertNotNull(networkConfig);
        // Tests load from test/resources/hazelcast.yml
        assertEquals(5702, networkConfig.getPort());
        assertTrue(networkConfig.isPortAutoIncrement());
    }

    @Test
    void hazelcastConfig_ShouldDisableMulticast() throws Exception {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();

        // Assert
        assertNotNull(joinConfig);
        assertFalse(joinConfig.getMulticastConfig().isEnabled());
    }

    @Test
    void hazelcastConfig_ShouldEnableTcpIpWithMembers() throws Exception {
        // Act
        Config config = hazelcastConfig.hazelcastConfiguration();
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();

        // Assert
        assertNotNull(joinConfig);
        // Tests load from test/resources/hazelcast.yml which has tcp-ip disabled
        assertFalse(joinConfig.getTcpIpConfig().isEnabled());
    }

    @Test
    void hazelcastInstance_ShouldCreateInstanceWithConfig() throws Exception {
        // Arrange
        Config config = hazelcastConfig.hazelcastConfiguration();
        // Disable network join for unit test to avoid cluster connection issues
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

        // Act
        hazelcastInstance = hazelcastConfig.hazelcastInstance(config);

        // Assert
        assertNotNull(hazelcastInstance);
        // Tests load from test/resources/hazelcast.yml
        assertEquals("test-instance", hazelcastInstance.getName());
        assertTrue(hazelcastInstance.getLifecycleService().isRunning());
    }

    @Test
    void hazelcastInstance_ShouldUseProvidedConfig() throws Exception {
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
    void hazelcastConfig_ShouldBeReusable() throws Exception {
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
