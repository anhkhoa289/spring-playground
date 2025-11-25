package com.khoa.spring.playground.aspect;

import com.khoa.spring.playground.annotation.LogExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * AOP Aspect for logging execution time and request details
 * Intercepts methods annotated with @LogExecutionTime
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Around("@annotation(logExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, LogExecutionTime logExecutionTime) throws Throwable {
        long startTime = System.currentTimeMillis();
        Instant startInstant = Instant.now();

        // Get HTTP request details
        HttpServletRequest request = null;
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            request = attributes.getRequest();
        }

        // Get method details
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // Build log prefix
        String logPrefix = buildLogPrefix(request, className, methodName, logExecutionTime.value());

        // Log request details
        logRequestDetails(logPrefix, request, joinPoint);

        Object result;
        try {
            // Execute the method
            result = joinPoint.proceed();

            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Log success with response details
            logSuccess(logPrefix, result, executionTime);

            return result;
        } catch (Exception e) {
            // Calculate execution time even on error
            long executionTime = System.currentTimeMillis() - startTime;

            // Log error
            logError(logPrefix, e, executionTime);

            throw e;
        }
    }

    private String buildLogPrefix(HttpServletRequest request, String className, String methodName, String description) {
        StringBuilder prefix = new StringBuilder();

        if (request != null) {
            prefix.append("[")
                  .append(request.getMethod())
                  .append(" ")
                  .append(request.getRequestURI());

            if (request.getQueryString() != null) {
                prefix.append("?").append(request.getQueryString());
            }

            prefix.append("]");
        }

        prefix.append(" ").append(className).append(".").append(methodName);

        if (description != null && !description.isEmpty()) {
            prefix.append(" - ").append(description);
        }

        return prefix.toString();
    }

    private void logRequestDetails(String prefix, HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Request Start ==========\n");
        logMessage.append(prefix).append("\n");

        if (request != null) {
            logMessage.append("Client IP: ").append(getClientIP(request)).append("\n");
            logMessage.append("User-Agent: ").append(request.getHeader("User-Agent")).append("\n");
        }

        // Log method arguments
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();

            logMessage.append("Parameters: ");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) logMessage.append(", ");
                logMessage.append(parameterNames[i]).append("=").append(formatArgument(args[i]));
            }
            logMessage.append("\n");
        }

        logMessage.append("===================================");
        log.info(logMessage.toString());
    }

    private void logSuccess(String prefix, Object result, long executionTime) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Request Complete ==========\n");
        logMessage.append(prefix).append("\n");
        logMessage.append("Execution Time: ").append(executionTime).append(" ms\n");

        if (result instanceof ResponseEntity<?>) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            logMessage.append("Response Status: ").append(response.getStatusCode()).append("\n");
            logMessage.append("Response Body Type: ").append(
                response.getBody() != null ? response.getBody().getClass().getSimpleName() : "null"
            ).append("\n");
        } else {
            logMessage.append("Response Type: ").append(
                result != null ? result.getClass().getSimpleName() : "void"
            ).append("\n");
        }

        logMessage.append("======================================");
        log.info(logMessage.toString());
    }

    private void logError(String prefix, Exception e, long executionTime) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Request Failed ==========\n");
        logMessage.append(prefix).append("\n");
        logMessage.append("Execution Time: ").append(executionTime).append(" ms\n");
        logMessage.append("Error Type: ").append(e.getClass().getSimpleName()).append("\n");
        logMessage.append("Error Message: ").append(e.getMessage()).append("\n");
        logMessage.append("=====================================");
        log.error(logMessage.toString(), e);
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String formatArgument(Object arg) {
        if (arg == null) {
            return "null";
        }

        // Don't log full request body objects, just their type
        if (arg.getClass().getPackage() != null &&
            arg.getClass().getPackage().getName().startsWith("com.khoa.spring.playground")) {
            return "[" + arg.getClass().getSimpleName() + "]";
        }

        return String.valueOf(arg);
    }
}
