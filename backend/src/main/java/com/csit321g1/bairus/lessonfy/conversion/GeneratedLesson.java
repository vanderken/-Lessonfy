package com.csit321g1.bairus.lessonfy.conversion;

import java.util.List;

/**
 * The shared lesson shape returned by BOTH conversion methods. It maps
 * directly onto the existing LessonRequest / panels[] structure used when a
 * lesson is saved, so the review screen can send it unchanged.
 */
public record GeneratedLesson(String title, List<GeneratedSection> sections) {

    public record GeneratedSection(String title, String content) {
    }
}
