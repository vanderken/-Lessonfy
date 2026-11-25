package com.csit321g1.bairus.lessonfy.controller;

import java.util.Map;

import com.csit321g1.bairus.lessonfy.config.SecurityConfig;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the React app get a CSRF cookie before its first state-changing request. */
@RestController
@RequestMapping("/api/csrf")
public class CsrfController {

    @GetMapping
    public Map<String, String> token(CsrfToken token) {
        return Map.of(
                "headerName", SecurityConfig.CSRF_HEADER_NAME,
                "token", token.getToken());
    }
}
