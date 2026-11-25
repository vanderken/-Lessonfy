package com.csit321g1.bairus.lessonfy.service;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.entity.Collection;
import com.csit321g1.bairus.lessonfy.entity.User;
import com.csit321g1.bairus.lessonfy.repository.CollectionRepository;
import com.csit321g1.bairus.lessonfy.repository.UserRepository;

@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    public CollectionService(CollectionRepository collectionRepository, UserRepository userRepository) {
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse> getCollections(Long userId) {
        Long resolvedUserId = userId;
        return collectionRepository.findByUserIdOrderByNameAsc(resolvedUserId).stream()
            .map(collection -> new CollectionResponse(collection.getId(), collection.getName(), collection.getColor(), collection.getCreatedAt().toString()))
            .toList();
    }

    @Transactional
    public CollectionResponse createCollection(Long userId, CollectionRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        Collection collection = new Collection();
        collection.setUser(user);
        collection.setName(Optional.ofNullable(request.name()).map(String::trim).filter(value -> !value.isEmpty()).orElse("New Collection"));
        collection.setColor(Optional.ofNullable(request.color()).map(String::trim).filter(value -> !value.isEmpty()).orElse("#3b82f6"));

        try {
            Collection saved = collectionRepository.save(collection);
            return new CollectionResponse(saved.getId(), saved.getName(), saved.getColor(), saved.getCreatedAt().toString());
        } catch (DataIntegrityViolationException ex) {
            // The collection_unique_name_per_user index rejects two collections
            // with the same name for the same user (case-insensitive).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You already have a collection with this name.");
        }
    }

    @Transactional
    public CollectionResponse updateCollection(Long collectionId, Long userId, CollectionRequest request) {
        Collection collection = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found."));

        if (!collection.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this collection.");
        }

        collection.setName(Optional.ofNullable(request.name()).map(String::trim).filter(value -> !value.isEmpty()).orElse(collection.getName()));
        collection.setColor(Optional.ofNullable(request.color()).map(String::trim).filter(value -> !value.isEmpty()).orElse(collection.getColor()));
        try {
            collectionRepository.save(collection);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You already have a collection with this name.");
        }
        return new CollectionResponse(collection.getId(), collection.getName(), collection.getColor(), collection.getCreatedAt().toString());
    }

    @Transactional
    public void deleteCollection(Long collectionId, Long userId) {
        Collection collection = collectionRepository.findById(collectionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found."));

        if (!collection.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete this collection.");
        }
        collectionRepository.delete(collection);
    }

    public record CollectionRequest(String name, String color) {
    }

    public record CollectionResponse(Long id, String name, String color, String createdAt) {
    }
}
