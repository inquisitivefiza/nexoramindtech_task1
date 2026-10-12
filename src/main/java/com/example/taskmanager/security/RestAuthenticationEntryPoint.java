package com.example.taskmanager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns a JSON 401 for missing / invalid / expired tokens. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(JsonErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message = reason != null
                ? reason.toString()
                : "Authentication required. Provide a valid 'Authorization: Bearer <token>' header";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, message);
    }
}
