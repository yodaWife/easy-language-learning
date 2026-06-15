package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.Account;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for user accounts.
 */
public interface AccountRepository {

    /**
     * Find an account by its stable identifier.
     *
     * @param userId UUID-string user ID
     * @return the account, or empty if not found
     */
    Optional<Account> findById(String userId);

    /**
     * Find an account by display name (case-insensitive).
     *
     * @param displayName the user's display name
     * @return the account, or empty if not found
     */
    Optional<Account> findByDisplayName(String displayName);

    /**
     * Return all known accounts, ordered by display name ascending.
     */
    List<Account> findAll();

    /**
     * Persist a new or updated account.
     * If an account with the same {@code userId} already exists it is replaced.
     *
     * @param account the account to save
     * @return the saved account (may be the same object)
     */
    Account save(Account account);
}
