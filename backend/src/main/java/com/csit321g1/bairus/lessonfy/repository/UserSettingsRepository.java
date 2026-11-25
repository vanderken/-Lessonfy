package com.csit321g1.bairus.lessonfy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csit321g1.bairus.lessonfy.entity.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    Optional<UserSettings> findByUserId(Long userId);
}
