package com.searchable.core;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that SLF4J + Logback is wired correctly. */
class LoggingSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmokeTest.class);

    @Test
    void loggerIsObtainedAndProducesNoExceptions() {
        log.info("info-level log line");
        log.debug("debug-level log line: {}", 42);
        log.warn("warn-level log line");
        // No assertion needed; the test only verifies the logger chain is wired.
        assertThat(log.getName()).isEqualTo(LoggingSmokeTest.class.getName());
    }

    @Test
    void mdcContextIsPropagated() {
        MDC.put("traceId", "test-trace-1234");
        try {
            log.info("log line with MDC traceId");
            assertThat(MDC.get("traceId")).isEqualTo("test-trace-1234");
        } finally {
            MDC.clear();
        }
    }
}
