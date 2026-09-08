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

        // 1. Preflight (OPTIONS) zahtevi moraju uvek proći bez filtriranja
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. WHITELIST za login i register (ne provera se token)
        // NAPOMENA: /api/auth/me NE SME biti whitelisted jer mu TREBA token da prepozna ulogovanog korisnika!
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            filterChain.doFilter(request, response);
            return; // Obavezan return da se filter ne bi ponovo izvršio u nastavku!
        }

        // 3. Ekstrakcija i verifikacija JWT tokena za sve ostale rute (uključujući /api/auth/me)
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Ignorišemo slučajne nevalidne stringove iz localStorage-a
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

        // 4. Nastavak lanca filtera (samo JEDAN poziv na kraju)
        filterChain.doFilter(request, response);
    }
}