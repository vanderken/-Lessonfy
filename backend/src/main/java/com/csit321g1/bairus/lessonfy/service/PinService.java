package com.csit321g1.bairus.lessonfy.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.entity.Collection;
import com.csit321g1.bairus.lessonfy.entity.Lesson;
import com.csit321g1.bairus.lessonfy.entity.Pin;
import com.csit321g1.bairus.lessonfy.entity.Section;
import com.csit321g1.bairus.lessonfy.repository.CollectionRepository;
import com.csit321g1.bairus.lessonfy.repository.LessonRepository;
import com.csit321g1.bairus.lessonfy.repository.PinRepository;
import com.csit321g1.bairus.lessonfy.repository.UserRepository;

@Service
public class PinService {

    /** Matches the VARCHAR(500) column on core_pin.text_snippet. */
    private static final int MAX_TEXT_LENGTH = 500;

    private final PinRepository pinRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final CollectionRepository collectionRepository;

    public PinService(PinRepository pinRepository, UserRepository userRepository, LessonRepository lessonRepository,
            CollectionRepository collectionRepository) {
        this.pinRepository = pinRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.collectionRepository = collectionRepository;
    }

    @Transactional(readOnly = true)
    public List<PinResponse> getPins(Long userId, Long lessonId) {
        List<Pin> pins = lessonId == null
            ? pinRepository.findBySectionLessonUserIdOrderByCreatedAtDesc(userId)
            : pinRepository.findBySectionLessonUserIdAndSectionLessonIdOrderByCreatedAtDesc(userId, lessonId);

        return pins.stream()
            .map(PinService::toResponse)
            .toList();
    }

    @Transactional
    public PinResponse createPin(Long userId, PinRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }

        if (request.lessonId() == null || request.sectionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lessonId and sectionId are required.");
        }

        Lesson lesson = lessonRepository.findById(request.lessonId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found."));
        if (!lesson.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This lesson does not belong to the user.");
        }

        Section section = lesson.getSections().stream()
            .filter(item -> item.getId().equals(request.sectionId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found."));

        Set<Collection> collections = resolveCollections(userId, request.collectionIds());
        if (collections.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one collectionId is required.");
        }

        Pin pin = new Pin();
        pin.setSection(section);
        pin.setSelectedText(truncateText(request.selectedText()));
        pin.setStartPosition(Optional.ofNullable(request.startPosition()).orElse(0));
        pin.setEndPosition(Optional.ofNullable(request.endPosition()).orElse(0));
        pin.setParagraphIndex(Math.max(0, Optional.ofNullable(request.paragraphIndex()).orElse(0)));
        pin.setCollections(collections);
        validateOffsets(pin);

        Pin saved = pinRepository.save(pin);
        return toResponse(saved);
    }

    @Transactional
    public PinResponse updatePin(Long pinId, Long userId, PinRequest request) {
        Pin pin = pinRepository.findById(pinId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pin not found."));
        if (!pin.getSection().getLesson().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this pin.");
        }

        if (request.selectedText() != null) {
            pin.setSelectedText(truncateText(request.selectedText()));
        }
        if (request.startPosition() != null) {
            pin.setStartPosition(request.startPosition());
        }
        if (request.endPosition() != null) {
            pin.setEndPosition(request.endPosition());
        }
        if (request.paragraphIndex() != null) {
            pin.setParagraphIndex(Math.max(0, request.paragraphIndex()));
        }

        // Only replace collections when the request actually includes them;
        // a null list keeps the existing one, an empty one is rejected because
        // core_pin.collection_id is NOT NULL.
        if (request.collectionIds() != null) {
            Set<Collection> collections = resolveCollections(userId, request.collectionIds());
            if (collections.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one collectionId is required.");
            }
            pin.setCollections(collections);
        }

        validateOffsets(pin);
        return toResponse(pin);
    }

    @Transactional
    public void deletePin(Long pinId, Long userId) {
        Pin pin = pinRepository.findById(pinId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pin not found."));
        if (!pin.getSection().getLesson().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete this pin.");
        }
        pinRepository.delete(pin);
    }

    private Set<Collection> resolveCollections(Long userId, List<Long> collectionIds) {
        Set<Collection> collections = new HashSet<>();
        for (Long collectionId : collectionIds) {
            Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found."));
            if (!collection.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Collection does not belong to this user.");
            }
            collections.add(collection);
        }
        return collections;
    }

    private static String truncateText(String text) {
        String value = Optional.ofNullable(text).orElse("");
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private static void validateOffsets(Pin pin) {
        int start = Optional.ofNullable(pin.getStartPosition()).orElse(0);
        int end = Optional.ofNullable(pin.getEndPosition()).orElse(0);
        if (end < start) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endPosition must be greater than or equal to startPosition.");
        }
    }

    private static PinResponse toResponse(Pin pin) {
        return new PinResponse(
            pin.getId(),
            pin.getLesson() != null ? pin.getLesson().getId() : null,
            pin.getSection().getId(),
            pin.getSelectedText(),
            pin.getStartPosition(),
            pin.getEndPosition(),
            pin.getParagraphIndex(),
            pin.getCollections().stream().map(Collection::getId).toList(),
            pin.getCreatedAt().toString());
    }

    public record PinRequest(Long lessonId, Long sectionId, String selectedText, Integer startPosition, Integer endPosition,
            Integer paragraphIndex, List<Long> collectionIds) {
    }

    public record PinResponse(Long id, Long lessonId, Long sectionId, String selectedText, Integer startPosition,
            Integer endPosition, Integer paragraphIndex, List<Long> collectionIds, String createdAt) {
    }
}
