package com.csit321g1.bairus.lessonfy.conversion;

/** AI draft plus which provider actually produced it. */
public record AiResult(GeneratedLesson lesson, String provider, boolean usedFallback) {
}
