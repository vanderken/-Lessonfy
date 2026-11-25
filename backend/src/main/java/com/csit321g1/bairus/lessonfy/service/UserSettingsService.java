package com.csit321g1.bairus.lessonfy.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.entity.User;
import com.csit321g1.bairus.lessonfy.entity.UserSettings;
import com.csit321g1.bairus.lessonfy.repository.UserRepository;
import com.csit321g1.bairus.lessonfy.repository.UserSettingsRepository;

@Service
public class UserSettingsService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public UserSettingsService(UserRepository userRepository, UserSettingsRepository userSettingsRepository) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = new UserSettings();
            settings.setUser(user);
            settings.setTheme("light");
            settings.setBackground("default");
            settings.setBionicReadingEnabled(true);
            settings.setFocusModeEnabled(true);
            settings.setFontSize(19);
            settings.setLineSpacing(1.7);
            settings.setSoundType("off");
            settings.setSoundVolume(40);
            settings.setTimerPreset("25-5");
            settings = userSettingsRepository.save(settings);
            user.setSettings(settings);
        }

        return new UserSettingsResponse(
            settings.getId(),
            settings.isBionicReadingEnabled(),
            settings.getFontSize(),
            settings.getLineSpacing(),
            settings.isFocusModeEnabled(),
            settings.getTheme(),
            settings.getBackground(),
            settings.getSoundType(),
            settings.getSoundVolume(),
            settings.getTimerPreset()
        );
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UserSettingsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        UserSettings settings = user.getSettings();
        if (settings == null) {
            settings = new UserSettings();
            settings.setUser(user);
        }

        settings.setBionicReadingEnabled(request.bionicReadingEnabled() != null ? request.bionicReadingEnabled() : settings.isBionicReadingEnabled());
        settings.setFontSize(request.fontSize() != null ? request.fontSize() : settings.getFontSize());
        settings.setLineSpacing(request.lineSpacing() != null ? request.lineSpacing() : settings.getLineSpacing());
        settings.setFocusModeEnabled(request.focusModeEnabled() != null ? request.focusModeEnabled() : settings.isFocusModeEnabled());
        settings.setTheme(request.theme() != null && !request.theme().isBlank() ? request.theme() : settings.getTheme());
        settings.setBackground(request.background() != null && !request.background().isBlank() ? request.background() : settings.getBackground());
        settings.setSoundType(request.soundType() != null && !request.soundType().isBlank() ? request.soundType() : settings.getSoundType());
        settings.setSoundVolume(request.soundVolume() != null ? request.soundVolume() : settings.getSoundVolume());
        settings.setTimerPreset(request.timerPreset() != null && !request.timerPreset().isBlank() ? request.timerPreset() : settings.getTimerPreset());

        user.setSettings(settings);
        UserSettings saved = userSettingsRepository.save(settings);

        return new UserSettingsResponse(
            saved.getId(),
            saved.isBionicReadingEnabled(),
            saved.getFontSize(),
            saved.getLineSpacing(),
            saved.isFocusModeEnabled(),
            saved.getTheme(),
            saved.getBackground(),
            saved.getSoundType(),
            saved.getSoundVolume(),
            saved.getTimerPreset()
        );
    }

    public record UserSettingsRequest(Boolean bionicReadingEnabled, Integer fontSize, Double lineSpacing,
            Boolean focusModeEnabled, String theme, String background, String soundType, Double soundVolume,
            String timerPreset) {
    }

    public record UserSettingsResponse(Long id, Boolean bionicReadingEnabled, Integer fontSize, Double lineSpacing,
            Boolean focusModeEnabled, String theme, String background, String soundType, Double soundVolume,
            String timerPreset) {
    }
}
