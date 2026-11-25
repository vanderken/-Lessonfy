package com.csit321g1.bairus.lessonfy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.csit321g1.bairus.lessonfy.config.SecurityConfig;

import jakarta.servlet.http.Cookie;

@SpringBootTest
class LessonfyApplicationIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        // Use the real browser contract for every state change. MockMvc's csrf()
        // postprocessor replaces the application's cookie repository with a test
        // repository, which would hide exactly the behaviour we need to verify.
        MvcResult result = mockMvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value(SecurityConfig.CSRF_HEADER_NAME))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();
        this.csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        this.csrfToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        assertNotNull(csrfCookie, "GET /api/csrf must set the XSRF-TOKEN cookie.");
        assertNotNull(csrfToken, "GET /api/csrf must return the CSRF token.");
    }

    @Test
    void registerUserAndCreateLesson() throws Exception {
        String uniqueEmail = "ada-" + UUID.randomUUID() + "@example.com";
        MockHttpSession session = registerUser("Ada", "Lovelace", uniqueEmail);

        mockMvc.perform(withCsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", uniqueEmail, "password", "StrongPass1!"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(uniqueEmail));

        Long lessonId = createLesson(session, "Algorithms");

        mockMvc.perform(get("/api/lessons/" + lessonId).session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Algorithms"));
    }

    @Test
    void getLessonByIdReturnsLessonDetails() throws Exception {
        String uniqueEmail = "lesson-user-" + UUID.randomUUID() + "@example.com";
        MockHttpSession session = registerUser("Lesson", "User", uniqueEmail);

        Long lessonId = createLesson(session, "Algorithms");

        mockMvc.perform(get("/api/lessons/" + lessonId)
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(lessonId))
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.sections").isArray());

        // Another session (not signed in) must not be able to read this lesson.
        mockMvc.perform(get("/api/lessons/" + lessonId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationReturnsHelpfulErrorMessage() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";

        registerUser("Alice", "Wonder", email);

        mockMvc.perform(withCsrf(post("/api/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of(
                                "firstName", "Alice",
                                "lastName", "Again",
                                "email", email,
                                "password", "StrongPass1!"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("An account with this email already exists."));
    }

    @Test
    void csrfCookieIsAvailableAndStateChangesRequireCsrfToken() throws Exception {
        assertTrue(csrfCookie.getValue().equals(csrfToken),
                "The cookie and JSON token from /api/csrf must match.");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", "nobody@example.com", "password", "StrongPass1!"))))
            .andExpect(status().isForbidden());

        MockHttpSession session = registerUser("Csrf", "User", "csrf-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(post("/api/collections")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Important\",\"color\":\"#FFD166\"}"))
            .andExpect(status().isForbidden());

        // The browser flow: send the token back in X-XSRF-TOKEN with the cookie.
        mockMvc.perform(withCsrf(post("/api/collections"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Important\",\"color\":\"#FFD166\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Important"));
    }

    @Test
    void oneUserCannotReadOrChangeAnotherUsersLesson() throws Exception {
        MockHttpSession owner = registerUser("Owner", "User", "owner-" + UUID.randomUUID() + "@example.com");
        MockHttpSession other = registerUser("Other", "User", "other-" + UUID.randomUUID() + "@example.com");

        Long lessonId = createLesson(owner, "Private lesson");

        mockMvc.perform(get("/api/lessons/" + lessonId).session(other))
            .andExpect(status().isNotFound());

        mockMvc.perform(withCsrf(put("/api/lessons/" + lessonId))
                .session(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("title", "Stolen", "panels", List.of()))))
            .andExpect(status().isNotFound());

        mockMvc.perform(withCsrf(delete("/api/lessons/" + lessonId))
                .session(other))
            .andExpect(status().isNotFound());

        // The real owner still has the lesson.
        mockMvc.perform(get("/api/lessons/" + lessonId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Private lesson"));
    }

    @Test
    void publicUserListingEndpointIsRemoved() throws Exception {
        MockHttpSession session = registerUser("No", "Listing", "nolisting-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/users").session(session))
            .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder.cookie(csrfCookie).header(SecurityConfig.CSRF_HEADER_NAME, csrfToken);
    }

    private MockHttpSession registerUser(String firstName, String lastName, String email) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of(
                                "firstName", firstName,
                                "lastName", lastName,
                                "email", email,
                                "password", "StrongPass1!"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.email").value(email))
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createLesson(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/lessons"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of(
                                "title", title,
                                "subtitle", "Intro to problem solving",
                                "panels", List.of(
                                        Map.of(
                                                "title", "Intro",
                                                "content", "Algorithms make problem solving easier.",
                                                "position", 0))))))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
