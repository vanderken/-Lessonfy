package com.csit321g1.bairus.lessonfy.conversion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Backup provider using Anthropic's Claude Messages API at platform.claude.com.
 * The key stays in Spring Boot and is sent only to Anthropic.
 */
@Component
public class ClaudeAiLessonProvider implements AiLessonProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiLessonProvider.class);
    private static final String PROVIDER = "claude";
    private static final int MAX_OUTPUT_TOKENS = 8192;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${lessonfy.ai.claude.api-key:}")
    private String apiKey;

    @Value("${lessonfy.ai.claude.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${lessonfy.ai.claude.model:claude-haiku-5-5}")
    private String model;

    @Value("${lessonfy.ai.claude.version:2023-06-01}")
    private String version;

    @Value("${lessonfy.ai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${lessonfy.ai.max-input-chars:20000}")
    private int maxInputChars;

    public ClaudeAiLessonProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint) {
        return generate(documentText, titleHint, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public GeneratedLesson generate(String documentText, String titleHint, Duration timeout) {
        String responseBody = send(buildRequestBody(documentText, titleHint), timeout);
        return parseResponse(responseBody);
    }

    /** Package-private so the request shape can be tested directly. */
    Map<String, Object> buildRequestBody(String documentText, String titleHint) {
        String source = AiLessonSupport.prepareSource(documentText, maxInputChars, PROVIDER);

        return Map.of(
                "model", model,
                "max_tokens", MAX_OUTPUT_TOKENS,
                "system", AiLessonSupport.SYSTEM_PROMPT,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", AiLessonSupport.formatUserPrompt(titleHint, source))));
    }

    /** Package-private so output validation can be tested directly. */
    GeneratedLesson parseResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode content = root == null ? null : root.path("content");
            StringBuilder text = new StringBuilder();
            if (content != null && content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText(""))) {
                        text.append(block.path("text").asText(""));
                    }
                }
            }
            return AiLessonSupport.parseLessonJson(objectMapper, text.toString(), PROVIDER);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiLessonSupport.badOutput(PROVIDER);
        }
    }

    private String send(Map<String, Object> body, Duration timeout) {
        if (!isConfigured()) {
            throw new AiProviderException(AiProviderException.Kind.NOT_CONFIGURED, PROVIDER,
                    "Claude is not configured. Add ANTHROPIC_API_KEY to the backend environment and restart Spring Boot.");
        }

        String url = baseUrl.replaceAll("/+$", "") + "/v1/messages";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", version)
                    .timeout(effectiveTimeout(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 400) {
                log.warn("Claude rejected the request (HTTP 400).");
                throw new AiProviderException(AiProviderException.Kind.INVALID_REQUEST, PROVIDER,
                        "Claude rejected the request. Check CLAUDE_MODEL, then try again or use Semantic-Based Conversion.");
            }
            if (status == 401 || status == 403) {
                log.warn("Claude authentication failed (HTTP {}).", status);
                throw new AiProviderException(AiProviderException.Kind.AUTH, PROVIDER,
                        "The Claude API key is missing or invalid. Check ANTHROPIC_API_KEY and restart the backend.");
            }
            if (status == 429) {
                log.warn("Claude rate limit reached (HTTP 429).");
                throw new AiProviderException(AiProviderException.Kind.QUOTA, PROVIDER,
                        "Claude's rate limit is full right now. Trying another AI, or use Semantic-Based Conversion.");
            }
            if (status < 200 || status >= 300) {
                log.warn("Claude returned HTTP {}.", status);
                throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                        "Claude is temporarily unavailable. Trying another AI, or use Semantic-Based Conversion.");
            }
            return response.body();
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException(AiProviderException.Kind.TIMEOUT, PROVIDER,
                    "Claude took too long to respond. Trying another AI, or use Semantic-Based Conversion.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Kind.SERVER, PROVIDER,
                    "AI generation was interrupted. Please try again.");
        } catch (IOException ex) {
            log.warn("Could not contact Claude: {}", ex.getMessage());
            throw new AiProviderException(AiProviderException.Kind.NETWORK, PROVIDER,
                    "Cannot reach Claude. Trying another AI, or use Semantic-Based Conversion.");
        }
    }

    private Duration effectiveTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Duration.ofSeconds(Math.max(1, timeoutSeconds));
        }
        return timeout;
    }
}
