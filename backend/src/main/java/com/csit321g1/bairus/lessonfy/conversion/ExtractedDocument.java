package com.csit321g1.bairus.lessonfy.conversion;

import java.util.List;

/** Text extracted from an uploaded file, kept with its document structure. */
public record ExtractedDocument(String text, List<Block> blocks, String suggestedTitle) {

    /**
     * level 0 = normal paragraph, 1+ = heading / title (larger number = deeper).
     */
    public record Block(int level, String text) {
    }
}
