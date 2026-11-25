package com.csit321g1.bairus.lessonfy.config;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** Small in-memory throttle for password guessing and registration spam. */
@Component
public class AuthRateLimitService {

    private static final int LOGIN_MAX_FAILURES = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int REGISTER_MAX_REQUESTS = 20;
    private static final Duration REGISTER_WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Bucket> loginFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registrations = new ConcurrentHashMap<>();

    public void checkLogin(String email) {
        Bucket bucket = loginFailures.get(loginKey(email));
        if (bucket != null && bucket.active() && bucket.count >= LOGIN_MAX_FAILURES) {
            throw tooMany("Too many failed sign-in attempts. Wait 15 minutes, then try again.");
        }
    }

    public void recordLoginFailure(String email) {
        increment(loginFailures, loginKey(email), LOGIN_WINDOW);
    }

    public void recordLoginSuccess(String email) {
        loginFailures.remove(loginKey(email));
    }

    public void checkRegister() {
        Bucket bucket = registrations.get(clientIp());
        if (bucket != null && bucket.active() && bucket.count >= REGISTER_MAX_REQUESTS) {
            throw tooMany("Too many accounts created from this connection. Wait 10 minutes, then try again.");
        }
    }

    public void recordRegister() {
        increment(registrations, clientIp(), REGISTER_WINDOW);
    }

    private void increment(ConcurrentHashMap<String, Bucket> map, String key, Duration window) {
        long now = System.currentTimeMillis();
        map.compute(key, (ignored, bucket) -> {
            if (bucket == null || !bucket.active()) {
                return new Bucket(1, now + window.toMillis());
            }
            return new Bucket(bucket.count + 1, bucket.expiresAt);
        });
        if (map.size() > 2000) {
            map.entrySet().removeIf(entry -> !entry.getValue().active());
        }
    }

    private String loginKey(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return clientIp() + "|" + value;
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String ip = servletAttributes.getRequest().getRemoteAddr();
            if (ip != null && !ip.isBlank()) return ip;
        }
        return "unknown";
    }

    private ResponseStatusException tooMany(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private record Bucket(int count, long expiresAt) {
        boolean active() {
            return expiresAt > System.currentTimeMillis();
        }
    }
}
