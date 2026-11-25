package com.csit321g1.bairus.lessonfy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csit321g1.bairus.lessonfy.service.UserService;
import com.csit321g1.bairus.lessonfy.service.UserSettingsService;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;

    public UserController(UserService userService, UserSettingsService userSettingsService) {
        this.userService = userService;
        this.userSettingsService = userSettingsService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserService.UserResponse> register(@RequestBody UserService.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<UserService.UserResponse> login(@RequestBody UserService.LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsService.UserSettingsResponse> getSettings() {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(userSettingsService.getSettings(resolvedUserId));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsService.UserSettingsResponse> updateSettings(
            @RequestBody UserSettingsService.UserSettingsRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        return ResponseEntity.ok(userSettingsService.updateSettings(resolvedUserId, request));
    }

    @PutMapping("/account/password")
    public ResponseEntity<Void> changePassword(@RequestBody UserService.ChangePasswordRequest request) {
        Long resolvedUserId = userService.resolveUserId();
        userService.changePassword(resolvedUserId, request);
        return ResponseEntity.noContent().build();
    }
}
