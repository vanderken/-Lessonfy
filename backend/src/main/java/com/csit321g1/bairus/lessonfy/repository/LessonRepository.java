package com.csit321g1.bairus.lessonfy.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csit321g1.bairus.lessonfy.entity.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    Page<Lesson> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Lesson> findByUserIdAndTitleContainingIgnoreCase(Long userId, String title, Pageable pageable);

    Optional<Lesson> findByIdAndUserId(Long id, Long userId);

    // Fetches the lesson together with its sections in one query so the
    // sections are never touched lazily outside of a session (this was
    // the cause of the "Cannot lazily initialize collection ... sections ...
    // (no session)" crash when open-in-view is disabled).
    @Query("select distinct l from Lesson l left join fetch l.sections where l.id = :id and l.user.id = :userId")
    Optional<Lesson> findByIdAndUserIdWithSections(@Param("id") Long id, @Param("userId") Long userId);
}
