package org.socialbridge.crm.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-ID";
    private static final String ATTRIBUTE_NAME = RequestTracingFilter.class.getName() + ".traceId";
    private static final String MDC_KEY = "traceId";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String suppliedId = request.getHeader(HEADER_NAME);
        String traceId = suppliedId != null && suppliedId.length() == 36 && UUID_PATTERN.matcher(suppliedId).matches()
                ? UUID.fromString(suppliedId).toString()
                : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE_NAME, traceId);
        response.setHeader(HEADER_NAME, traceId);
        String previousId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previousId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousId);
            }
        }
    }

    static String traceId(HttpServletRequest request) {
        Object existingId = request.getAttribute(ATTRIBUTE_NAME);
        if (existingId instanceof String value) {
            return value;
        }
        String generatedId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE_NAME, generatedId);
        return generatedId;
    }
}
