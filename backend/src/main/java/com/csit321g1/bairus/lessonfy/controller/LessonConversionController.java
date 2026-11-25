package com.csit321g1.bairus.lessonfy.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.csit321g1.bairus.lessonfy.conversion.GeneratedLesson;
import com.csit321g1.bairus.lessonfy.conversion.LessonConversionService;
import com.csit321g1.bairus.lessonfy.service.UserService;

/** Generates a lesson DRAFT. Saving is handled by the existing /api/lessons endpoint. */
@RestController
@RequestMapping("/api/lessons")
public class LessonConversionController {

    private final LessonConversionService conversionService;
    private final UserService userService;

    public LessonConversionController(LessonConversionService conversionService, UserService userService) {
        this.conversionService = conversionService;
        this.userService = userService;
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeneratedLesson> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("method") String method) {
        // The user must be signed in, exactly as for every other lesson endpoint.
        userService.resolveUserId();
        LessonConversionService.ConversionResult result = conversionService.convertDetailed(file, method);
        return ResponseEntity.ok()
                .header("X-Lessonfy-Generator", result.generator())
                .header("X-Lessonfy-Fallback", String.valueOf(result.usedFallback()))
                .body(result.lesson());
    }
}
