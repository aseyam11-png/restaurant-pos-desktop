package com.restaurantpos.localapi.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalTokenFilter extends OncePerRequestFilter {
    private final String configuredToken;

    public LocalTokenFilter(@Value("${pos.security.local-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || path.equals("/actuator/health")
            || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (configuredToken.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            writeError(response, "LOCAL_API_NOT_CONFIGURED", "Local API access token is not configured.");
            return;
        }

        String supplied = request.getHeader("X-POS-Local-Token");
        if (supplied == null || !MessageDigest.isEqual(
            configuredToken.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        )) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "UNAUTHORIZED", "A valid local API token is required.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
            "{"type":"about:blank","title":"Unauthorized","status":"
                + response.getStatus() + ","code":"" + code + "","detail":"" + message + ""}"
        );
    }
}