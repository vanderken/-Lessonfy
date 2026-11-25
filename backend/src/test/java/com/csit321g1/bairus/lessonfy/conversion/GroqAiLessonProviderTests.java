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

class GroqAiLessonProviderTests {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private GroqAiLessonProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new GroqAiLessonProvider(mapper);
        setField("apiKey", "test-key");
        setField("baseUrl", "https://api.groq.com/openai/v1");
        setField("model", "openai/gpt-oss-20b");
        setField("timeoutSeconds", 30);
        setField("maxInputChars", 20000);
    }

    @Test
    void validGroqChatCompletionBecomesSharedLessonStructure() throws Exception {
        String lessonJson = mapper.writeValueAsString(Map.of(
                "title", "Cell Structure",
                "sections", List.of(
                        Map.of("title", "The nucleus", "content", "The nucleus controls the cell."),
                        Map.of("title", "The membrane", "content", "The membrane controls entry."))));

        GeneratedLesson lesson = provider.parseResponse(chatResponse(lessonJson));

        assertEquals("Cell Structure", lesson.title());
        assertEquals(2, lesson.sections().size());
        assertEquals("The membrane", lesson.sections().get(1).title());
    }

    @Test
    void malformedResponseIsRejected() throws Exception {
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.parseResponse(chatResponse("Here is your lesson:")));

        assertEquals(AiProviderException.Kind.BAD_OUTPUT, error.kind());
        assertTrue(error.getMessage().contains("unexpected format"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestUsesOpenAICompatibleJsonSchema() {
        Map<String, Object> body = provider.buildRequestBody(
                "The nucleus controls the cell.", "Biology notes");

        assertEquals("openai/gpt-oss-20b", body.get("model"));

        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("ONLY facts"));
        assertEquals("user", messages.get(1).get("role"));
        assertTrue(messages.get(1).get("content").contains("The nucleus controls the cell."));
        assertTrue(messages.get(1).get("content").contains("Biology notes"));

        Map<String, Object> responseFormat = (Map<String, Object>) body.get("response_format");
        assertEquals("json_schema", responseFormat.get("type"));
        Map<String, Object> schema = (Map<String, Object>) responseFormat.get("json_schema");
        assertEquals(Boolean.TRUE, schema.get("strict"));
        assertTrue(schema.get("schema").toString().contains("sections"));
        assertEquals(8192, body.get("max_tokens"));
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
        assertTrue(error.getMessage().contains("GROQ_API_KEY"));
    }

    private String chatResponse(String content) {
        try {
            return mapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                    "message", Map.of("role", "assistant", "content", content)))));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field field = GroqAiLessonProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(provider, value);
    }
}
