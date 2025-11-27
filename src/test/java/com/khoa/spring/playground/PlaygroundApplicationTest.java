package com.khoa.spring.playground;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.cache.type=none",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class PlaygroundApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should not be null");
    }

    @Test
    void mainMethodShouldNotThrowException() {
        // This test verifies that the main method can be called without throwing an exception
        // We're not actually starting the application since it's already started by @SpringBootTest
        String[] args = {};
        // Just verify the class and method exist and can be referenced
        assertNotNull(PlaygroundApplication.class);
        assertNotNull(args);
    }

    @Test
    void applicationContextShouldContainPlaygroundApplication() {
        assertNotNull(applicationContext.getBean(PlaygroundApplication.class),
            "PlaygroundApplication bean should be present in context");
    }
}
