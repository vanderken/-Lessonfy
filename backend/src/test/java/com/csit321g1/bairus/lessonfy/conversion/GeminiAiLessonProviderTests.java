package com.csit321g1.bairus.lessonfy.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GeminiAiLessonProviderTests {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private GeminiAiLessonProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new GeminiAiLessonProvider(mapper);
        setField("apiKey", "test-key");
        setField("baseUrl", "https://generativelanguage.googleapis.com");
        setField("model", "gemini-3.5-flash-lite");
        setField("timeoutSeconds", 30);
        setField("maxInputChars", 20000);
    }

    @Test
    void validGeminiJsonBecomesSharedLessonStructure() throws Exception {
        String lessonJson = mapper.writeValueAsString(Map.of(
                "title", "Database Systems",
                "sections", List.of(
                        Map.of("title", "What is a database?", "content", "A database stores information."),
                        Map.of("title", "Why it matters", "content", "It helps retrieve data quickly."))));
        String geminiResponse = geminiResponse(lessonJson);

        GeneratedLesson lesson = provider.parseResponse(geminiResponse);

        assertEquals("Database Systems", lesson.title());
        assertEquals(2, lesson.sections().size());
        assertEquals("Why it matters", lesson.sections().get(1).title());
    }

    @Test
    void codeFencedJsonIsAccepted() throws Exception {
        String lessonJson = mapper.writeValueAsString(Map.of(
                "title", "Photosynthesis",
                "sections", List.of(Map.of("title", "Overview", "content", "Plants make glucose."))));

        assertEquals("Photosynthesis",
                provider.parseResponse(geminiResponse("```json\n" + lessonJson + "\n```")).title());
    }

    @Test
    void malformedAndEmptyResponsesAreRejected() throws Exception {
        AiProviderException malformed = assertThrows(AiProviderException.class,
                () -> provider.parseResponse(geminiResponse("I cannot help with that")));
        assertEquals(AiProviderException.Kind.BAD_OUTPUT, malformed.kind());
        assertTrue(malformed.getMessage().contains("unexpected format"));

        AiProviderException missingSections = assertThrows(AiProviderException.class,
                () -> provider.parseResponse(geminiResponse("{\"title\":\"A\",\"sections\":[]}")));
        assertEquals(AiProviderException.Kind.BAD_OUTPUT, missingSections.kind());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCarriesDocumentStrictPromptAndJsonSchema() {
        Map<String, Object> body = provider.buildRequestBody(
                "A database is an organized collection of information.", "Chapter 1");

        Map<String, Object> system = (Map<String, Object>) body.get("system_instruction");
        List<Map<String, String>> systemParts = (List<Map<String, String>>) system.get("parts");
        assertTrue(systemParts.get(0).get("text").contains("ONLY facts"));
        assertTrue(systemParts.get(0).get("text").contains("ADHD"));

        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals(1, contents.size());
        List<Map<String, String>> userParts = (List<Map<String, String>>) contents.get(0).get("parts");
        assertTrue(userParts.get(0).get("text").contains("A database is an organized collection of information."));
        assertTrue(userParts.get(0).get("text").contains("Chapter 1"));

        Map<String, Object> generation = (Map<String, Object>) body.get("generationConfig");
        assertEquals("application/json", generation.get("responseMimeType"));
        assertTrue(generation.get("responseSchema").toString().contains("sections"));
    }

    @Test
    void controlCharactersAreStrippedFromDocumentText() {
        Map<String, Object> body = provider.buildRequestBody("Hello\u200B\u0001 world", "Title\u0002");

        assertTrue(body.toString().contains("Hello world"));
        assertTrue(!body.toString().contains("\u200B"));
    }

    @Test
    void missingApiKeyGivesAFriendlyConfigurationError() throws Exception {
        setField("apiKey", "");

        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.generate("Some lesson text", "Title"));

        assertEquals(AiProviderException.Kind.NOT_CONFIGURED, error.kind());
        assertTrue(error.getMessage().contains("GEMINI_API_KEY"));
    }

    private String geminiResponse(String text) {
        try {
            return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", text)))))));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GeminiAiLessonProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(provider, value);
    }
}
