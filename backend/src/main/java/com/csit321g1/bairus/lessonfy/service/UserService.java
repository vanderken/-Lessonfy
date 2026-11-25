package com.csit321g1.bairus.lessonfy.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.csit321g1.bairus.lessonfy.config.AuthRateLimitService;
import com.csit321g1.bairus.lessonfy.config.SessionAuthFilter;
import com.csit321g1.bairus.lessonfy.entity.Collection;
import com.csit321g1.bairus.lessonfy.entity.User;
import com.csit321g1.bairus.lessonfy.entity.UserSettings;
import com.csit321g1.bairus.lessonfy.repository.CollectionRepository;
import com.csit321g1.bairus.lessonfy.repository.UserRepository;

@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w+.-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UserRepository userRepository;
    private final CollectionRepository collectionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService rateLimitService;

    public UserService(UserRepository userRepository, CollectionRepository collectionRepository,
            PasswordEncoder passwordEncoder, AuthRateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.collectionRepository = collectionRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        rateLimitService.checkRegister();
        String firstName = Optional.ofNullable(request.firstName()).map(String::trim).orElse("");
        String lastName = Optional.ofNullable(request.lastName()).map(String::trim).orElse("");
        // Legacy fallback: older clients may still send a single "name" field.
        String legacyName = Optional.ofNullable(request.name()).map(String::trim).orElse("");
        if (firstName.isBlank() && !legacyName.isBlank()) {
            String[] parts = legacyName.split("\\s+", 2);
            firstName = parts[0];
            if (lastName.isBlank()) {
                lastName = parts.length > 1 ? parts[1] : "";
            }
        }
        String email = Optional.ofNullable(request.email()).map(String::trim).map(value -> value.toLowerCase()).orElse("");
        String password = request.password();

        if (firstName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name is required.");
        }
        if (lastName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name is required.");
        }
        if (firstName.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "First name is too long.");
        }
        if (lastName.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Last name is too long.");
        }

        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters.");
        }
        if (password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        UserSettings settings = new UserSettings();
        settings.setTheme("light");
        settings.setBionicReadingEnabled(true);
        settings.setFocusModeEnabled(true);
        settings.setFontSize(19);
        settings.setLineSpacing(1.7);
        settings.setSoundType("off");
        settings.setSoundVolume(0.4);
        user.setSettings(settings);

        User saved = userRepository.save(user);
        establishSession(saved.getId());
        rateLimitService.recordRegister();

        List<String> defaultColors = List.of("#FFD166", "#EF476F", "#06D6A0");
        List<String> defaultNames = List.of("Come back to this", "Confusing", "Study this");
        for (int i = 0; i < defaultNames.size(); i++) {
            Collection collection = new Collection();
            collection.setUser(saved);
            collection.setName(defaultNames.get(i));
            collection.setColor(defaultColors.get(i));
            collectionRepository.save(collection);
        }

        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail());
    }

    public UserResponse login(LoginRequest request) {
        String email = Optional.ofNullable(request.email()).map(String::trim).map(value -> value.toLowerCase()).orElse("");
        String password = request.password();
        rateLimitService.checkLogin(email);

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> {
            rateLimitService.recordLoginFailure(email);
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        });

        if (!passwordEncoder.matches(password, user.getPassword())) {
            rateLimitService.recordLoginFailure(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }

        rateLimitService.recordLoginSuccess(email);
        establishSession(user.getId());
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    /**
     * Returns the id of the currently signed-in user. The id comes only from the
     * server-side session (set at login/registration) — never from a URL or
     * request parameter, so one user cannot act on another user's data.
     */
    public Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    private void establishSession(Long userId) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                // New ID on login prevents session fixation.
                request.changeSessionId();
            }
            HttpSession session = existing == null ? request.getSession(true) : existing;
            session.setAttribute(SessionAuthFilter.SESSION_USER_KEY, userId);
        }
    }

    public User findUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        String currentPassword = request.currentPassword();
        String newPassword = request.newPassword();

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required.");
        }
        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public record RegisterRequest(String firstName, String lastName, String name, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record UserResponse(Long id, String name, String email) {
    }
}
