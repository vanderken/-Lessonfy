package com.csit321g1.bairus.lessonfy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csit321g1.bairus.lessonfy.entity.Pin;

public interface PinRepository extends JpaRepository<Pin, Long> {

    // Pin has no user/lesson columns; ownership is reached through section -> lesson -> user.
    List<Pin> findBySectionLessonUserIdOrderByCreatedAtDesc(Long userId);

    List<Pin> findBySectionLessonUserIdAndSectionLessonIdOrderByCreatedAtDesc(Long userId, Long lessonId);
}
