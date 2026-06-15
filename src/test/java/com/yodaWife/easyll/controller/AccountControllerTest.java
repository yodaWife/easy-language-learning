package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.ActiveUserContext;
import com.yodawife.easyll.domain.SessionAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AccountControllerTest extends AbstractControllerIntegrationTest {

    private static final Path TEMP_ACCOUNTS_DIR;

    static {
        try {
            TEMP_ACCOUNTS_DIR = Files.createTempDirectory("account-test-");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp dir for account tests", e);
        }
    }

    @DynamicPropertySource
    static void isolateAccountStorage(DynamicPropertyRegistry registry) {
        // Redirect account writes to a temp dir so tests never touch the real users.csv.
        registry.add("app.accounts.file-path", () -> TEMP_ACCOUNTS_DIR.resolve("users-test.csv").toString());
    }

    // ── GET /account/panel ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /account/panel returns account-panel fragment with users and activeUser")
    void panel_returnsFragmentWithUsersAndActiveUser() throws Exception {
        mockMvc.perform(get("/account/panel"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/account-panel :: panel"))
                .andExpect(model().attributeExists("users", "activeUser"));
    }

    @Test
    @DisplayName("GET /account/panel with signed-in session reflects active user")
    void panel_withSignedInSession_reflectsActiveUser() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute(SessionAttributes.ACTIVE_USER, ActiveUserContext.of("uid-1", "Ewa"));

        var result = mockMvc.perform(get("/account/panel").session(session))
                .andExpect(status().isOk())
                .andReturn();

        var activeUser = Objects.requireNonNull((ActiveUserContext) Objects.requireNonNull(
                result.getModelAndView()).getModel().get("activeUser"));
        assertThat(activeUser).isNotNull();
        assertThat(activeUser.signedIn()).isTrue();
        assertThat(activeUser.displayName()).isEqualTo("Ewa");
    }

    // ── POST /account/sign-in ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /account/sign-in with valid name returns account-status fragment signed in")
    void signIn_withValidName_returnsSignedInStatus() throws Exception {
        mockMvc.perform(post("/account/sign-in").param("displayName", "TestPlayer"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/account-status :: status"))
                .andExpect(model().attributeExists("activeUser"));
    }

    @Test
    @DisplayName("POST /account/sign-in sets signed-in context in session")
    void signIn_setsContextInSession() throws Exception {
        var session = new MockHttpSession();

        mockMvc.perform(post("/account/sign-in").param("displayName", "TestPlayer").session(session))
                .andExpect(status().isOk());

        var stored = (ActiveUserContext) session.getAttribute(SessionAttributes.ACTIVE_USER);
        assertThat(stored).isNotNull();
        assertThat(Objects.requireNonNull(stored).signedIn()).isTrue();
        assertThat(stored.displayName()).isEqualTo("TestPlayer");
    }

    @Test
    @DisplayName("POST /account/sign-in with blank name returns anonymous status without modifying session")
    void signIn_withBlankName_returnsAnonymousStatus() throws Exception {
        var result = mockMvc.perform(post("/account/sign-in").param("displayName", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/account-status :: status"))
                .andReturn();

        var activeUser = Objects.requireNonNull((ActiveUserContext) Objects.requireNonNull(
                result.getModelAndView()).getModel().get("activeUser"));
        assertThat(activeUser.signedIn()).isFalse();
    }

    @Test
    @DisplayName("POST /account/sign-in without displayName param returns anonymous status")
    void signIn_withoutParam_returnsAnonymousStatus() throws Exception {
        var result = mockMvc.perform(post("/account/sign-in"))
                .andExpect(status().isOk())
                .andReturn();

        var activeUser = Objects.requireNonNull((ActiveUserContext) Objects.requireNonNull(
                result.getModelAndView()).getModel().get("activeUser"));
        assertThat(activeUser.signedIn()).isFalse();
    }

    // ── POST /account/sign-out ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /account/sign-out clears session and returns anonymous status")
    void signOut_clearsSessionAndReturnsAnonymousStatus() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute(SessionAttributes.ACTIVE_USER, ActiveUserContext.of("uid-1", "Ewa"));

        var result = mockMvc.perform(post("/account/sign-out").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/account-status :: status"))
                .andReturn();

        assertThat(session.getAttribute(SessionAttributes.ACTIVE_USER)).isNull();
        var activeUser = Objects.requireNonNull((ActiveUserContext) Objects.requireNonNull(
                result.getModelAndView()).getModel().get("activeUser"));
        assertThat(activeUser.signedIn()).isFalse();
    }

    // ── GET /account/status ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /account/status returns account-status fragment")
    void status_returnsFragment() throws Exception {
        mockMvc.perform(get("/account/status"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/account-status :: status"))
                .andExpect(model().attributeExists("activeUser"));
    }
}
