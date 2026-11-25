package com.csit321g1.bairus.lessonfy.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csit321g1.bairus.lessonfy.service.LessonService;
import com.csit321g1.bairus.lessonfy.service.UserService;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final UserService userService;

    public LessonController(LessonService lessonService, UserService userService) {
        this.lessonService = lessonService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLessons(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {

        Long resolvedUserId = userService.resolveUserId();
        Page<LessonService.LessonSummaryResponse> page = lessonService.findLessons(resolvedUserId, search, pageable);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", page.getContent());
        payload.put("totalItems", page.getTotalElements());
        payload.put("totalPages", page.getTotalPages());
        payload.put("page", page.getNumber());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LessonService.LessonResponse> getLesson(@PathVariable Long id) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(lessonService.getLesson(id, resolvedUserId));
    }

    @PostMapping
    public ResponseEntity<LessonService.LessonResponse> createLesson(
            @RequestBody LessonService.LessonRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.createLesson(resolvedUserId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LessonService.LessonResponse> updateLesson(
            @PathVariable Long id,
            @RequestBody LessonService.LessonRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(lessonService.updateLesson(id, resolvedUserId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable Long id) {
        Long resolvedUserId = userService.resolveUserId();
        lessonService.deleteLesson(id, resolvedUserId);
        return ResponseEntity.noContent().build();
    }
}
