package com.psytrance.psytrance_tracker_backend.filter;

import com.psytrance.psytrance_tracker_backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. Preflight (OPTIONS) zahtevi odmah prolaze
        if ("OPTIONS".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Javno čitanje recenzija, login i registracija ne traže proveru tokena
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || (path.startsWith("/api/reviews/event/") && "GET".equalsIgnoreCase(method))) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Ekstrakcija i verifikacija JWT tokena za zaštićene rute
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (!token.equals("undefined") && !token.equals("null") && !token.isBlank()) {
                try {
                    String username = jwtUtil.extractUsername(token);

                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(username, null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception e) {
                    System.out.println(">>> Invalid JWT: " + e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}