package com.csit321g1.bairus.lessonfy.config;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Turns the Lessonfy session (which stores the logged-in userId) into a Spring
 * Security authentication. This is the ONLY place the current user is decided:
 * clients can never supply a userId via URL parameters or request bodies.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_USER_KEY = "lessonfyUserId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Object userId = session.getAttribute(SESSION_USER_KEY);
            if (userId instanceof Long id) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(id, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
