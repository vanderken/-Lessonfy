package com.csit321g1.bairus.lessonfy.conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rule-based conversion. It NEVER calls an AI service: it only groups the
 * document's own blocks and paragraphs using document structure.
 */
@Service
public class SemanticLessonService {

    private static final int TARGET_SECTION_CHARS = 1000;
    private static final int TITLE_MAX_CHARS = 200;
    private static final Pattern SENTENCE = Pattern.compile(
            "[^.!?\\n]+[.!?]+[\"'”’)]*\\s*|[^.!?]+$", Pattern.DOTALL);

    public GeneratedLesson generate(ExtractedDocument document) {
        List<RawSection> rawSections = groupByHeadings(document.blocks());
        if (rawSections.isEmpty()) {
            rawSections = groupByLength(document.blocks());
        }

        List<GeneratedLesson.GeneratedSection> sections = new ArrayList<>();
        for (RawSection raw : rawSections) {
            String body = trimTo(raw.body.toString(), 20000);
            if (body.isBlank()) {
                continue;
            }
            List<String> pieces = splitIntoReadablePieces(body);
            for (int i = 0; i < pieces.size(); i++) {
                String title = pieces.size() == 1
                        ? raw.title
                        : raw.title + " (part " + (i + 1) + ")";
                sections.add(new GeneratedLesson.GeneratedSection(
                        trimTo(title, TITLE_MAX_CHARS), pieces.get(i)));
            }
        }

        if (sections.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No readable lesson content was found. Try a different document.");
        }
        return new GeneratedLesson(trimTo(titleOf(document), TITLE_MAX_CHARS), sections);
    }

    private List<RawSection> groupByHeadings(List<ExtractedDocument.Block> blocks) {
        List<RawSection> sections = new ArrayList<>();
        RawSection current = null;
        for (ExtractedDocument.Block block : blocks) {
            if (block.level() > 0) {
                // A heading with no paragraphs under it is not a lesson section.
                if (current != null && current.body.length() == 0) {
                    sections.remove(sections.size() - 1);
                }
                current = new RawSection(block.text());
                sections.add(current);
            } else {
                if (current == null) {
                    current = new RawSection(titleFromText(block.text()));
                    sections.add(current);
                }
                if (current.body.length() > 0) current.body.append("\n\n");
                current.body.append(block.text());
            }
        }
        if (current != null && current.body.length() == 0) {
            sections.remove(sections.size() - 1);
        }
        return sections;
    }

    /** Fallback for documents with no headings at all. */
    private List<RawSection> groupByLength(List<ExtractedDocument.Block> blocks) {
        List<RawSection> sections = new ArrayList<>();
        RawSection current = null;
        for (ExtractedDocument.Block block : blocks) {
            if (current == null || current.body.length() > TARGET_SECTION_CHARS) {
                current = new RawSection(titleFromText(block.text()));
                sections.add(current);
            } else {
                current.body.append("\n\n");
            }
            current.body.append(block.text());
        }
        return sections;
    }

    private static String titleFromText(String text) {
        String firstSentence = text.split("(?<=[.!?])\\s+", 2)[0].trim();
        if (firstSentence.isEmpty()) firstSentence = text.trim();
        if (firstSentence.length() > 60) {
            firstSentence = firstSentence.substring(0, 60).trim();
        }
        return firstSentence.isEmpty() ? "Lesson overview" : firstSentence;
    }

    private static String titleOf(ExtractedDocument document) {
        String title = document.suggestedTitle();
        return title == null || title.isBlank() ? "Untitled lesson" : title.trim();
    }

    private static List<String> splitIntoReadablePieces(String body) {
        List<String> pieces = new ArrayList<>();
        List<String> paragraphs = List.of(body.split("\\n\\s*\\n"));
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isEmpty()) continue;

            if (value.length() > TARGET_SECTION_CHARS) {
                flush(pieces, current);
                appendLongSentences(pieces, value);
                continue;
            }
            if (current.length() > 0 && current.length() + value.length() + 2 > TARGET_SECTION_CHARS) {
                flush(pieces, current);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(value);
        }
        flush(pieces, current);
        return pieces.isEmpty() ? List.of(body.trim()) : pieces;
    }

    /** Splits an oversized paragraph on sentence boundaries. */
    private static void appendLongSentences(List<String> pieces, String paragraph) {
        Matcher matcher = SENTENCE.matcher(paragraph);
        StringBuilder current = new StringBuilder();
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            String sentence = matcher.group().trim();
            if (sentence.isEmpty()) continue;

            while (sentence.length() > TARGET_SECTION_CHARS) {
                flush(pieces, current);
                int cut = sentence.lastIndexOf(' ', TARGET_SECTION_CHARS);
                if (cut < 1) cut = TARGET_SECTION_CHARS;
                pieces.add(sentence.substring(0, cut).trim());
                sentence = sentence.substring(cut).trim();
            }
            if (current.length() > 0 && current.length() + sentence.length() + 1 > TARGET_SECTION_CHARS) {
                flush(pieces, current);
            }
            if (current.length() > 0) current.append(' ');
            current.append(sentence);
        }
        if (!matched) {
            pieces.add(paragraph);
            return;
        }
        flush(pieces, current);
    }

    private static void flush(List<String> pieces, StringBuilder current) {
        if (current.length() > 0) {
            String value = current.toString().trim();
            if (!value.isEmpty()) pieces.add(value);
            current.setLength(0);
        }
    }

    private static String trimTo(String value, int maxChars) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() > maxChars ? trimmed.substring(0, maxChars) : trimmed;
    }

    private static final class RawSection {
        private final String title;
        private final StringBuilder body = new StringBuilder();

        private RawSection(String title) {
            this.title = trimTo(title, TITLE_MAX_CHARS).isEmpty()
                    ? "Lesson overview"
                    : trimTo(title, TITLE_MAX_CHARS);
        }
    }
}
