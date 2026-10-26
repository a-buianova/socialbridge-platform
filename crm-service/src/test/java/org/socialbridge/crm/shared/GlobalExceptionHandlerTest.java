package org.socialbridge.crm.shared;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestTracingFilter())
                .build();
    }

    @Test
    void overlappingTransactionFailuresReturnConflictWithSafeRetryAdvice() throws Exception {
        String traceId = UUID.randomUUID().toString();
        mvc.perform(get("/test/optimistic").header("X-Request-ID", traceId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-ID", traceId))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.detail").value("Contact was modified concurrently; reload it and retry"));
        mvc.perform(get("/test/jpa-optimistic")).andExpect(status().isConflict());
    }

    @Test
    void unexpectedFailureDoesNotReflectExceptionOrDatabaseDetails() throws Exception {
        String response = mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("synthetic@example.org", "SELECT", "IllegalStateException");
    }

    @Test
    void tracingRestoresExistingMdcEvenWhenTheFilterChainFails() {
        RequestTracingFilter filter = new RequestTracingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String requestId = UUID.randomUUID().toString();
        request.addHeader("X-Request-ID", requestId);
        MDC.put("traceId", "outer-request");
        try {
            assertThatThrownBy(() -> filter.doFilter(request, response, (incoming, outgoing) -> {
                assertThat(MDC.get("traceId")).isEqualTo(requestId);
                throw new IOException("Synthetic filter-chain failure");
            })).isInstanceOf(IOException.class);
            assertThat(MDC.get("traceId")).isEqualTo("outer-request");
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void tracingDoesNotLeaveMdcOnTheWorkerThread() throws Exception {
        assertThat(MDC.get("traceId")).isNull();
        RequestTracingFilter filter = new RequestTracingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) ->
                assertThat(UUID.fromString(MDC.get("traceId"))).isNotNull());
        assertThat(MDC.get("traceId")).isNull();
    }

    @RestController
    static class FailureController {
        @GetMapping("/test/optimistic")
        void springOptimisticFailure() {
            throw new OptimisticLockingFailureException("Synthetic overlapping transaction");
        }

        @GetMapping("/test/jpa-optimistic")
        void jpaOptimisticFailure() {
            throw new jakarta.persistence.OptimisticLockException("Synthetic stale version");
        }

        @GetMapping("/test/failure")
        void failure() {
            throw new IllegalStateException("SELECT synthetic@example.org");
        }
    }
}
