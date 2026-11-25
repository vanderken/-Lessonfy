package com.csit321g1.bairus.lessonfy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csit321g1.bairus.lessonfy.entity.Collection;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    List<Collection> findByUserIdOrderByNameAsc(Long userId);
}
