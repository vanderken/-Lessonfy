package com.csit321g1.bairus.lessonfy.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.csit321g1.bairus.lessonfy.conversion.AiLessonProvider;
import com.csit321g1.bairus.lessonfy.conversion.AiProviderChain;
import com.csit321g1.bairus.lessonfy.conversion.ClaudeAiLessonProvider;
import com.csit321g1.bairus.lessonfy.conversion.GeminiAiLessonProvider;
import com.csit321g1.bairus.lessonfy.conversion.GroqAiLessonProvider;

/** Wires the AI providers into one backend-only chain. */
@Configuration
public class AiProviderConfig {

    @Bean
    @Primary
    public AiProviderChain aiLessonProvider(
            GeminiAiLessonProvider gemini,
            GroqAiLessonProvider groq,
            ClaudeAiLessonProvider claude,
            @Value("${lessonfy.ai.providers:gemini,groq,claude}") String order,
            @Value("${lessonfy.ai.total-timeout-seconds:50}") int totalTimeoutSeconds) {
        return new AiProviderChain(
                List.of(gemini, groq, claude),
                order,
                Duration.ofSeconds(Math.max(5, totalTimeoutSeconds)));
    }
}
