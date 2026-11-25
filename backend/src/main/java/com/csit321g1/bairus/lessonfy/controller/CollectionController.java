package com.csit321g1.bairus.lessonfy.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csit321g1.bairus.lessonfy.service.CollectionService;
import com.csit321g1.bairus.lessonfy.service.UserService;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final UserService userService;

    public CollectionController(CollectionService collectionService, UserService userService) {
        this.collectionService = collectionService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<CollectionService.CollectionResponse>> getCollections() {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(collectionService.getCollections(resolvedUserId));
    }

    @PostMapping
    public ResponseEntity<CollectionService.CollectionResponse> createCollection(
            @RequestBody CollectionService.CollectionRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(collectionService.createCollection(resolvedUserId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CollectionService.CollectionResponse> updateCollection(
            @PathVariable Long id,
            @RequestBody CollectionService.CollectionRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(collectionService.updateCollection(id, resolvedUserId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollection(@PathVariable Long id) {
        Long resolvedUserId = userService.resolveUserId();
        collectionService.deleteCollection(id, resolvedUserId);
        return ResponseEntity.noContent().build();
    }
}
