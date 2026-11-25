package com.csit321g1.bairus.lessonfy.conversion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tries configured AI providers in order. It never exposes keys to the browser
 * and never saves a draft. Semantic-Based Conversion stays the guaranteed
 * no-key alternative.
 */
public class AiProviderChain implements AiLessonProvider {

    private static final Logger log = LoggerFactory.getLogger(AiProviderChain.class);
    private static final int FAILURES_BEFORE_COOLDOWN = 3;
    private static final Duration COOLDOWN = Duration.ofMinutes(10);

    private final Map<String, AiLessonProvider> providers = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Map<String, Failure> failures = new HashMap<>();
    private final Duration totalTimeout;

    public AiProviderChain(List<AiLessonProvider> candidates, String configuredOrder, Duration totalTimeout) {
        for (AiLessonProvider provider : candidates) {
            providers.put(provider.name().toLowerCase(Locale.ROOT), provider);
        }
        String cleaned = configuredOrder == null ? "" : configuredOrder;
        for (String part : cleaned.split(",")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            if (!providers.containsKey(name)) {
                log.warn("Unknown AI provider in AI_PROVIDER_ORDER: {}", name);
                continue;
            }
            if (!order.contains(name)) order.add(name);
        }
        for (String name : providers.keySet()) {
            if (!order.contains(name)) order.add(name);
        }
        this.totalTimeout = totalTimeout;
    }

    @Override
    public String name() {
        return "chain";
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint) {
        return generateDetailed(documentText, titleHint).lesson();
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint, Duration timeout) {
        return generateDetailed(documentText, titleHint).lesson();
    }

    public AiResult generateDetailed(String documentText, String titleHint) {
        Instant deadline = Instant.now().plus(totalTimeout);
        boolean anyConfigured = false;
        boolean attempted = false;
        AiProviderException lastError = null;

        for (int index = 0; index < order.size(); index++) {
            String name = order.get(index);
            AiLessonProvider provider = providers.get(name);
            if (!provider.isConfigured()) continue;
            anyConfigured = true;

            if (isCoolingDown(name)) {
                log.info("Skipping AI provider {} while it cools down.", name);
                continue;
            }

            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isZero() || remaining.isNegative()) {
                log.warn("AI total timeout reached before provider {}.", name);
                break;
            }

            attempted = true;
            try {
                GeneratedLesson lesson = provider.generate(documentText, titleHint, remaining);
                failures.remove(name);
                return new AiResult(lesson, name, index > 0);
            } catch (AiProviderException ex) {
                lastError = ex;
                recordFailure(name, ex);
                log.warn("AI provider {} failed with {}.", name, ex.kind());
                if (!ex.retryable()) continue;
            } catch (RuntimeException ex) {
                lastError = new AiProviderException(AiProviderException.Kind.SERVER, name,
                        "AI generation failed unexpectedly. Try again, or use Semantic-Based Conversion.");
                recordFailure(name, lastError);
                log.warn("AI provider {} failed unexpectedly.", name);
            }
        }

        if (!anyConfigured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI is not configured. Add GEMINI_API_KEY, GROQ_API_KEY, or ANTHROPIC_API_KEY to the backend environment and restart Spring Boot.");
        }
        if (!attempted && lastError == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "All AI providers are cooling down. Wait a moment, or use Semantic-Based Conversion.");
        }

        HttpStatus status = lastError == null ? HttpStatus.BAD_GATEWAY : lastError.httpStatus();
        String message = lastError == null
                ? "AI generation is unavailable right now. Use Semantic-Based Conversion."
                : lastError.getMessage();
        throw new ResponseStatusException(status, message);
    }

    private boolean isCoolingDown(String name) {
        Failure failure = failures.get(name);
        return failure != null && failure.retryAfter() > System.currentTimeMillis();
    }

    private void recordFailure(String name, AiProviderException error) {
        if (error.kind() == AiProviderException.Kind.NOT_CONFIGURED) return;
        Failure previous = failures.get(name);
        int count = previous == null ? 1 : previous.count() + 1;
        long retryAfter = count >= FAILURES_BEFORE_COOLDOWN
                ? System.currentTimeMillis() + COOLDOWN.toMillis()
                : 0L;
        failures.put(name, new Failure(count, retryAfter));
    }

    private record Failure(int count, long retryAfter) {
    }
}
