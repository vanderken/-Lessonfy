package com.csit321g1.bairus.lessonfy.conversion;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Coordinates extraction and one of the two completely separate generators. */
@Service
public class LessonConversionService {

    public static final String METHOD_AI = "ai";
    public static final String METHOD_SEMANTIC = "semantic";

    private final DocumentExtractionService extractionService;
    private final SemanticLessonService semanticService;
    private final AiProviderChain aiChain;

    public LessonConversionService(DocumentExtractionService extractionService,
            SemanticLessonService semanticService,
            AiProviderChain aiChain) {
        this.extractionService = extractionService;
        this.semanticService = semanticService;
        this.aiChain = aiChain;
    }

    public GeneratedLesson convert(MultipartFile file, String method) {
        return convertDetailed(file, method).lesson();
    }

    public ConversionResult convertDetailed(MultipartFile file, String method) {
        ExtractedDocument document = extractionService.extract(file);
        return switch (normalise(method)) {
            case METHOD_AI -> {
                AiResult result = aiChain.generateDetailed(document.text(), document.suggestedTitle());
                yield new ConversionResult(result.lesson(), result.provider(), result.usedFallback());
            }
            case METHOD_SEMANTIC -> new ConversionResult(semanticService.generate(document), "semantic", false);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose AI-Powered Conversion or Semantic-Based Conversion.");
        };
    }

    public static String normalise(String method) {
        return method == null ? "" : method.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** DRAFT result plus the generator that produced it; still no saving here. */
    public record ConversionResult(GeneratedLesson lesson, String generator, boolean usedFallback) {
    }
}
