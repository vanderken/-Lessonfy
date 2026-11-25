package com.csit321g1.bairus.lessonfy.entity;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "core_pin")
public class Pin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "panel_id", nullable = false)
    private Section section;

    @ManyToOne
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @Column(name = "text_snippet", columnDefinition = "VARCHAR(500)", nullable = false)
    private String selectedText;

    @Column(name = "start_offset", nullable = false)
    private Integer startPosition;

    @Column(name = "end_offset", nullable = false)
    private Integer endPosition;

    @Column(name = "paragraph_index", nullable = false)
    private Integer paragraphIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    public Pin() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        // Ownership is reached through the section's lesson (the real mapping);
        // Pin has no direct user column.
        return section != null && section.getLesson() != null ? section.getLesson().getUser() : null;
    }

    public Lesson getLesson() {
        return section != null ? section.getLesson() : null;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public Integer getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(Integer startPosition) {
        this.startPosition = startPosition;
    }

    public Integer getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(Integer endPosition) {
        this.endPosition = endPosition;
    }

    public Integer getParagraphIndex() {
        return paragraphIndex;
    }

    public void setParagraphIndex(Integer paragraphIndex) {
        this.paragraphIndex = paragraphIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Set<Collection> getCollections() {
        if (collection == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Set.of(collection));
    }

    public void setCollections(Set<Collection> collections) {
        if (collections == null || collections.isEmpty()) {
            this.collection = null;
            return;
        }
        this.collection = collections.iterator().next();
    }
}
