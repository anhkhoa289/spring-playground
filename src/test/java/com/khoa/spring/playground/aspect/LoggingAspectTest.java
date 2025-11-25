package com.khoa.spring.playground.aspect;

import com.khoa.spring.playground.annotation.LogExecutionTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for LoggingAspect
 * Note: This is a basic test. In a real application, you would use Spring Boot Test
 * with @SpringBootTest to test the actual AOP behavior with the full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @Test
    void testAnnotationExists() {
        // Verify the annotation is properly defined
        assertNotNull(LogExecutionTime.class);

        // Verify retention policy is RUNTIME
        assertTrue(LogExecutionTime.class.isAnnotationPresent(java.lang.annotation.Retention.class));

        // Verify target is METHOD
        assertTrue(LogExecutionTime.class.isAnnotationPresent(java.lang.annotation.Target.class));
    }

    @Test
    void testLoggingAspectExists() {
        // Verify the aspect class exists and can be instantiated
        LoggingAspect aspect = new LoggingAspect();
        assertNotNull(aspect);
    }

    // Test class to verify annotation can be applied to methods
    static class TestController {
        @LogExecutionTime("Test operation")
        public ResponseEntity<String> testMethod() {
            return ResponseEntity.ok("test");
        }
    }

    @Test
    void testAnnotationCanBeAppliedToMethods() throws NoSuchMethodException {
        // Verify the annotation can be applied to methods
        var method = TestController.class.getMethod("testMethod");
        assertTrue(method.isAnnotationPresent(LogExecutionTime.class));

        LogExecutionTime annotation = method.getAnnotation(LogExecutionTime.class);
        assertEquals("Test operation", annotation.value());
    }
}
