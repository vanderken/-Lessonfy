package com.csit321g1.bairus.lessonfy.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AiProviderChainTests {

    @Test
    void retryableFailureFallsBackToNextProvider() {
        FakeProvider primary = new FakeProvider("primary", true)
                .failing(AiProviderException.Kind.QUOTA, "Primary rate limit reached.");
        FakeProvider backup = new FakeProvider("backup", true)
                .returning(lesson("Backup result"));
        AiProviderChain chain = new AiProviderChain(List.of(primary, backup), "primary,backup", Duration.ofSeconds(5));

        AiResult result = chain.generateDetailed("Document text", "Title");

        assertEquals("backup", result.provider());
        assertTrue(result.usedFallback());
        assertEquals("Backup result", result.lesson().title());
        assertEquals(1, primary.calls);
        assertEquals(1, backup.calls);
    }

    @Test
    void nonRetryableAuthFailureDoesNotStopTheChain() {
        FakeProvider primary = new FakeProvider("primary", true)
                .failing(AiProviderException.Kind.AUTH, "Primary key is invalid.");
        FakeProvider backup = new FakeProvider("backup", true)
                .returning(lesson("Backup result"));
        AiProviderChain chain = new AiProviderChain(List.of(primary, backup), "primary,backup", Duration.ofSeconds(5));

        AiResult result = chain.generateDetailed("Document text", "Title");

        assertEquals("backup", result.provider());
        assertTrue(result.usedFallback());
        assertEquals(1, primary.calls);
        assertEquals(1, backup.calls);
    }

    @Test
    void threeFailuresPutProviderIntoCooldown() {
        FakeProvider primary = new FakeProvider("primary", true)
                .failing(AiProviderException.Kind.SERVER, "Primary is down.");
        FakeProvider backup = new FakeProvider("backup", true)
                .returning(lesson("Backup result"));
        AiProviderChain chain = new AiProviderChain(List.of(primary, backup), "primary,backup", Duration.ofSeconds(5));

        for (int i = 0; i < 3; i++) {
            assertEquals("backup", chain.generateDetailed("Document text", "Title").provider());
        }
        assertEquals(3, primary.calls);

        AiResult result = chain.generateDetailed("Document text", "Title");

        assertEquals("backup", result.provider());
        assertEquals(3, primary.calls, "Cooling-down provider must not be called again.");
        assertEquals(4, backup.calls);
    }

    @Test
    void coolingDownPrimaryIsStillReportedAsFallback() {
        FakeProvider primary = new FakeProvider("primary", true)
                .failing(AiProviderException.Kind.TIMEOUT, "Primary timed out.");
        FakeProvider backup = new FakeProvider("backup", true)
                .returning(lesson("Backup result"));
        AiProviderChain chain = new AiProviderChain(List.of(primary, backup), "primary,backup", Duration.ofSeconds(5));

        for (int i = 0; i < 3; i++) {
            chain.generateDetailed("Document text", "Title");
        }

        AiResult result = chain.generateDetailed("Document text", "Title");
        assertEquals("backup", result.provider());
        assertTrue(result.usedFallback());
    }

    @Test
    void allUnconfiguredGivesHelpfulSetupMessage() {
        AiProviderChain chain = new AiProviderChain(List.of(
                new FakeProvider("gemini", false),
                new FakeProvider("groq", false),
                new FakeProvider("claude", false)), "gemini,groq,claude", Duration.ofSeconds(5));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> chain.generateDetailed("Document text", "Title"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("GEMINI_API_KEY"));
        assertTrue(error.getReason().contains("GROQ_API_KEY"));
        assertTrue(error.getReason().contains("ANTHROPIC_API_KEY"));
    }

    @Test
    void allFailuresSurfaceTheLastFriendlyMessage() {
        FakeProvider primary = new FakeProvider("primary", true)
                .failing(AiProviderException.Kind.QUOTA, "Primary rate limit reached.");
        FakeProvider backup = new FakeProvider("backup", true)
                .failing(AiProviderException.Kind.AUTH, "Backup key is invalid.");
        AiProviderChain chain = new AiProviderChain(List.of(primary, backup), "primary,backup", Duration.ofSeconds(5));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> chain.generateDetailed("Document text", "Title"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("Backup key is invalid.", error.getReason());
        assertFalse(error.getReason().contains("Exception"));
    }

    private static GeneratedLesson lesson(String title) {
        return new GeneratedLesson(title,
                List.of(new GeneratedLesson.GeneratedSection("Section", "Short lesson text.")));
    }

    private static final class FakeProvider implements AiLessonProvider {

        private final String providerName;
        private final boolean configured;
        private GeneratedLesson result;
        private AiProviderException failure;
        private int calls;

        private FakeProvider(String providerName, boolean configured) {
            this.providerName = providerName;
            this.configured = configured;
        }

        private FakeProvider returning(GeneratedLesson result) {
            this.result = result;
            this.failure = null;
            return this;
        }

        private FakeProvider failing(AiProviderException.Kind kind, String message) {
            this.failure = new AiProviderException(kind, providerName, message);
            this.result = null;
            return this;
        }

        @Override
        public String name() {
            return providerName;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public GeneratedLesson generate(String documentText, String titleHint) {
            return generate(documentText, titleHint, Duration.ofSeconds(5));
        }

        @Override
        public GeneratedLesson generate(String documentText, String titleHint, Duration timeout) {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
    }
}
