package com.csit321g1.bairus.lessonfy.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ClaudeAiLessonProviderTests {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private ClaudeAiLessonProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new ClaudeAiLessonProvider(mapper);
        setField("apiKey", "test-key");
        setField("baseUrl", "https://api.anthropic.com");
        setField("model", "claude-haiku-5-5");
        setField("version", "2023-06-01");
        setField("timeoutSeconds", 30);
        setField("maxInputChars", 20000);
    }

    @Test
    void validMessagesResponseBecomesSharedLessonStructure() throws Exception {
        String lessonJson = mapper.writeValueAsString(Map.of(
                "title", "Water Cycle",
                "sections", List.of(Map.of("title", "Evaporation", "content", "Water becomes vapour."))));

        GeneratedLesson lesson = provider.parseResponse(messagesResponse(lessonJson));

        assertEquals("Water Cycle", lesson.title());
        assertEquals(1, lesson.sections().size());
        assertEquals("Evaporation", lesson.sections().get(0).title());
    }

    @Test
    void multipleTextBlocksAreJoinedBeforeParsing() throws Exception {
        String response = mapper.writeValueAsString(Map.of("content", List.of(
                Map.of("type", "text", "text", "{\"title\":\"Rocks\","),
                Map.of("type", "text", "text", "\"sections\":[{\"title\":\"Igneous\",\"content\":\"Rock cools.\"}]}"))));

        assertEquals("Rocks", provider.parseResponse(response).title());
    }

    @Test
    void malformedResponseIsRejected() throws Exception {
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.parseResponse(messagesResponse("I cannot help with that")));

        assertEquals(AiProviderException.Kind.BAD_OUTPUT, error.kind());
        assertTrue(error.getMessage().contains("unexpected format"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestUsesMessagesApiShapeWithoutTemperature() {
        Map<String, Object> body = provider.buildRequestBody(
                "Water becomes vapour when heated.", "Science notes");

        assertEquals("claude-haiku-5-5", body.get("model"));
        assertEquals(8192, body.get("max_tokens"));
        assertTrue(((String) body.get("system")).contains("ONLY facts"));
        assertFalse(body.containsKey("temperature"), "Claude 4.7+ rejects non-default sampling values.");

        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("Water becomes vapour when heated."));
        assertTrue(messages.get(0).get("content").contains("Science notes"));
    }

    @Test
    void controlCharactersAreStrippedFromDocumentText() {
        Map<String, Object> body = provider.buildRequestBody("Hello\u200B\u0001 world", "Title\u0002");

        assertTrue(body.toString().contains("Hello world"));
        assertFalse(body.toString().contains("\u200B"));
    }

    @Test
    void missingApiKeyGivesAFriendlyConfigurationError() throws Exception {
        setField("apiKey", "");

        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.generate("Some lesson text", "Title"));

        assertEquals(AiProviderException.Kind.NOT_CONFIGURED, error.kind());
        assertTrue(error.getMessage().contains("ANTHROPIC_API_KEY"));
    }

    private String messagesResponse(String text) {
        try {
            return mapper.writeValueAsString(Map.of("content", List.of(
                    Map.of("type", "text", "text", text))));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ClaudeAiLessonProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(provider, value);
    }
}
