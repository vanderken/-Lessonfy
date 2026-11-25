package com.csit321g1.bairus.lessonfy.conversion;

import java.time.Duration;

/**
 * Provider boundary so the AI service can be swapped without touching the UI.
 * Every provider returns the same shared lesson structure.
 */
public interface AiLessonProvider {

    GeneratedLesson generate(String documentText, String titleHint);

    /** Short stable name used in logs and response headers. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** False when the provider has no key configured, so the chain can skip it. */
    default boolean isConfigured() {
        return true;
    }

    /** Optional shorter deadline supplied by the chain. */
    default GeneratedLesson generate(String documentText, String titleHint, Duration timeout) {
        return generate(documentText, titleHint);
    }
}
