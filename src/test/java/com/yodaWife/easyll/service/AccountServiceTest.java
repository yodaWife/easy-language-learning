package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.Account;
import com.yodawife.easyll.domain.ActiveUserContext;
import com.yodawife.easyll.domain.SessionAttributes;
import com.yodawife.easyll.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository repo = mock(AccountRepository.class);
    private final AccountService service = new AccountService(repo);

    private static Account testAccount(String id, String name) {
        return new Account(id, name, Instant.EPOCH);
    }

    // ── findAll ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll delegates to the repository")
    void findAll_delegatesToRepository() {
        var accounts = List.of(testAccount("id-1", "Ala"), testAccount("id-2", "Ewa"));
        when(repo.findAll()).thenReturn(accounts);

        assertThat(service.findAll()).isEqualTo(accounts);
    }

    // ── findOrCreate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findOrCreate returns existing account when displayName is found")
    void findOrCreate_returnsExistingAccount() {
        var existing = testAccount("id-1", "Ewa");
        when(repo.findByDisplayName("Ewa")).thenReturn(Optional.of(existing));

        var result = service.findOrCreate("Ewa");

        assertThat(result).isEqualTo(existing);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate creates and saves a new account when displayName is not found")
    void findOrCreate_createsNewAccountWhenNotFound() {
        when(repo.findByDisplayName("NewUser")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.findOrCreate("NewUser");

        assertThat(result.displayName()).isEqualTo("NewUser");
        assertThat(result.userId()).isNotBlank();
        verify(repo).save(any());
    }

    @Test
    @DisplayName("findOrCreate trims whitespace from displayName before lookup")
    void findOrCreate_trimsDisplayName() {
        when(repo.findByDisplayName("Ewa")).thenReturn(Optional.of(testAccount("id-1", "Ewa")));

        service.findOrCreate("  Ewa  ");

        verify(repo).findByDisplayName(eq("Ewa"));
    }

    // ── signIn ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("signIn stores ActiveUserContext in session and returns it")
    void signIn_storesContextInSession() {
        var account = testAccount("uid-1", "Ewa");
        when(repo.findByDisplayName("Ewa")).thenReturn(Optional.of(account));

        var session = new MockHttpSession();
        var ctx = service.signIn("Ewa", session);

        assertThat(ctx.signedIn()).isTrue();
        assertThat(ctx.userId()).isEqualTo("uid-1");
        assertThat(ctx.displayName()).isEqualTo("Ewa");
        assertThat(session.getAttribute(SessionAttributes.ACTIVE_USER)).isEqualTo(ctx);
    }

    // ── signOut ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("signOut removes ActiveUserContext from session")
    void signOut_removesContextFromSession() {
        var session = new MockHttpSession();
        session.setAttribute(SessionAttributes.ACTIVE_USER, ActiveUserContext.of("uid-1", "Ewa"));

        service.signOut(session);

        assertThat(session.getAttribute(SessionAttributes.ACTIVE_USER)).isNull();
    }

    // ── resolveActiveUser ─────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveActiveUser returns anonymous when session has no attribute")
    void resolveActiveUser_returnsAnonymousWhenNotSignedIn() {
        var ctx = service.resolveActiveUser(new MockHttpSession());

        assertThat(ctx.signedIn()).isFalse();
        assertThat(ctx.userId()).isNull();
    }

    @Test
    @DisplayName("resolveActiveUser returns the stored context when signed in")
    void resolveActiveUser_returnsStoredContextWhenSignedIn() {
        var stored = ActiveUserContext.of("uid-1", "Ewa");
        var session = new MockHttpSession();
        session.setAttribute(SessionAttributes.ACTIVE_USER, stored);

        assertThat(service.resolveActiveUser(session)).isEqualTo(stored);
    }
}
