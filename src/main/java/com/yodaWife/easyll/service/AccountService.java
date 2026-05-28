package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.Account;
import com.yodawife.easyll.domain.ActiveUserContext;
import com.yodawife.easyll.domain.SessionAttributes;
import com.yodawife.easyll.repository.AccountRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Return all known accounts, sorted by display name. */
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    /**
     * Find account by userId.
     */
    public Optional<Account> findById(String userId) {
        return accountRepository.findById(userId);
    }

    /**
     * Find existing account by displayName (case-insensitive) or create a new one.
     */
    public Account findOrCreate(String displayName) {
        String trimmed = displayName.trim();
        return accountRepository.findByDisplayName(trimmed)
                .orElseGet(() -> {
                    var newAccount = new Account(UUID.randomUUID().toString(), trimmed, Instant.now());
                    log.info("Creating new account: displayName='{}'", trimmed);
                    return accountRepository.save(newAccount);
                });
    }

    /**
     * Sign in with the given display name (find or create account),
     * write ActiveUserContext into the session, and return the new context.
     */
    public ActiveUserContext signIn(String displayName, HttpSession session) {
        var account = findOrCreate(displayName);
        var ctx = ActiveUserContext.of(account.userId(), account.displayName());
        // Note: we deliberately do not invalidate the session here because
        // doing so would discard in-flight game state (sessionId, languageCode, board).
        // All accounts in this app have equal privilege (no password auth), so session
        // fixation risk is limited. Consider rotating session ID only if true
        // authentication (passwords/tokens) is added in a future iteration.
        session.setAttribute(SessionAttributes.ACTIVE_USER, ctx);
        log.info("Signed in: userId='{}', displayName='{}'", account.userId(), account.displayName());
        return ctx;
    }

    /**
     * Sign out by removing ActiveUserContext from the session.
     */
    public void signOut(HttpSession session) {
        session.removeAttribute(SessionAttributes.ACTIVE_USER);
        log.info("Signed out.");
    }

    /**
     * Resolve the active user context from the session.
     * Returns {@link ActiveUserContext#anonymous()} if not signed in.
     */
    public ActiveUserContext resolveActiveUser(HttpSession session) {
        var ctx = session.getAttribute(SessionAttributes.ACTIVE_USER);
        if (ctx instanceof ActiveUserContext activeUserContext) {
            return activeUserContext;
        }
        return ActiveUserContext.anonymous();
    }
}
