package com.csit321g1.bairus.lessonfy.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.entity.Lesson;
import com.csit321g1.bairus.lessonfy.entity.Section;
import com.csit321g1.bairus.lessonfy.entity.User;
import com.csit321g1.bairus.lessonfy.repository.LessonRepository;
import com.csit321g1.bairus.lessonfy.repository.UserRepository;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    public LessonService(LessonRepository lessonRepository, UserRepository userRepository) {
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<LessonSummaryResponse> findLessons(Long userId, String search, Pageable pageable) {
        Long resolvedUserId = userId;
        if (search == null || search.isBlank()) {
            return lessonRepository.findByUserIdOrderByCreatedAtDesc(resolvedUserId, pageable)
                .map(this::toSummaryResponse);
        }

        return lessonRepository.findByUserIdAndTitleContainingIgnoreCase(resolvedUserId, search.trim(), pageable)
            .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public LessonResponse getLesson(Long lessonId, Long userId) {
        Lesson lesson = lessonRepository.findByIdAndUserIdWithSections(lessonId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        return toResponse(lesson);
    }

    @Transactional
    public LessonResponse createLesson(Long userId, LessonRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        Lesson lesson = new Lesson();
        lesson.setUser(user);
        lesson.setTitle(Optional.ofNullable(request.title()).map(String::trim).filter(value -> !value.isEmpty()).orElse("Untitled lesson"));
        lesson.setSubtitle(request.subtitle());

        List<SectionRequest> panels = Optional.ofNullable(request.panels()).orElseGet(List::of);
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < panels.size(); i++) {
            SectionRequest panel = panels.get(i);
            Section section = new Section();
            section.setLesson(lesson);
            // Show only user-provided titles; don't invent "Section N".
            section.setTitle(Optional.ofNullable(panel.title()).map(String::trim).orElse(""));
            section.setContent(panel.content());
            section.setPosition(panel.position() == null ? i : panel.position());
            sections.add(section);
        }

        lesson.setSections(sections);
        Lesson saved = lessonRepository.save(lesson);
        return toResponse(saved);
    }

    @Transactional
    public LessonResponse updateLesson(Long lessonId, Long userId, LessonRequest request) {
        Lesson lesson = lessonRepository.findByIdAndUserId(lessonId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));

        lesson.setTitle(Optional.ofNullable(request.title()).map(String::trim).filter(value -> !value.isEmpty()).orElse(lesson.getTitle()));
        lesson.setSubtitle(request.subtitle());

        lesson.getSections().clear();
        lessonRepository.flush();

        List<SectionRequest> panels = Optional.ofNullable(request.panels()).orElseGet(List::of);
        for (int i = 0; i < panels.size(); i++) {
            SectionRequest panel = panels.get(i);
            Section section = new Section();
            section.setLesson(lesson);
            section.setTitle(Optional.ofNullable(panel.title()).map(String::trim).orElse(""));
            section.setContent(panel.content());
            section.setPosition(panel.position() == null ? i : panel.position());
            lesson.getSections().add(section);
        }

        lesson.setUpdatedAt(LocalDateTime.now());
        return toResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public void deleteLesson(Long lessonId, Long userId) {
        Lesson lesson = lessonRepository.findByIdAndUserId(lessonId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        lessonRepository.delete(lesson);
    }

    private LessonSummaryResponse toSummaryResponse(Lesson lesson) {
        return new LessonSummaryResponse(
            lesson.getId(),
            lesson.getTitle(),
            lesson.getSubtitle(),
            lesson.getCreatedAt().toString(),
            lesson.getUpdatedAt().toString(),
            lesson.getSections() == null ? 0 : lesson.getSections().size()
        );
    }

    private LessonResponse toResponse(Lesson lesson) {
        List<SectionResponse> sections = lesson.getSections() == null
            ? List.of()
            : lesson.getSections().stream()
                .sorted(Comparator.comparing(Section::getPosition, Comparator.nullsLast(Integer::compareTo)))
                .map(section -> new SectionResponse(section.getId(), section.getTitle(), section.getContent(), section.getPosition()))
                .toList();

        return new LessonResponse(
            lesson.getId(),
            lesson.getTitle(),
            lesson.getSubtitle(),
            lesson.getCreatedAt().toString(),
            lesson.getUpdatedAt().toString(),
            sections
        );
    }

    public record LessonRequest(String title, String subtitle, List<SectionRequest> panels) {
    }

    public record SectionRequest(String title, String content, Integer position) {
    }

    public record LessonSummaryResponse(Long id, String title, String subtitle, String createdAt, String updatedAt, Integer panelCount) {
    }

    public record LessonResponse(Long id, String title, String subtitle, String createdAt, String updatedAt, List<SectionResponse> sections) {
    }

    public record SectionResponse(Long id, String title, String content, Integer position) {
    }
}
