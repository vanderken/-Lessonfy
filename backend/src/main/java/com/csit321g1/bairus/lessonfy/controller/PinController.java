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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.csit321g1.bairus.lessonfy.service.PinService;
import com.csit321g1.bairus.lessonfy.service.UserService;

@RestController
@RequestMapping("/api/pins")
public class PinController {

    private final PinService pinService;
    private final UserService userService;

    public PinController(PinService pinService, UserService userService) {
        this.pinService = pinService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<PinService.PinResponse>> getPins(
            @RequestParam(required = false) Long lessonId) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(pinService.getPins(resolvedUserId, lessonId));
    }

    @PostMapping
    public ResponseEntity<PinService.PinResponse> createPin(
            @RequestBody PinService.PinRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(pinService.createPin(resolvedUserId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PinService.PinResponse> updatePin(
            @PathVariable Long id,
            @RequestBody PinService.PinRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(pinService.updatePin(id, resolvedUserId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePin(@PathVariable Long id) {
        Long resolvedUserId = userService.resolveUserId();
        pinService.deletePin(id, resolvedUserId);
        return ResponseEntity.noContent().build();
    }
}
