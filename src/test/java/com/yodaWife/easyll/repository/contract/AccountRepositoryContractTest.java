package com.yodawife.easyll.repository.contract;

import com.yodawife.easyll.domain.Account;
import com.yodawife.easyll.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared contract tests that every AccountRepository implementation must pass.
 */
public abstract class AccountRepositoryContractTest {

    protected AccountRepository repository;

    /** Subclasses must provide a fresh, empty repository instance before each test. */
    @BeforeEach
    protected void setUpRepository() {
        repository = createRepository();
    }

    protected abstract AccountRepository createRepository();

    @Test
    @DisplayName("findById returns account when it exists")
    void findById_returnsAccount_whenExists() {
        var account = new Account("user-1", "Alice", Instant.now());
        repository.save(account);

        var found = repository.findById("user-1");

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo("user-1");
        assertThat(found.get().displayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findById returns empty when user does not exist")
    void findById_returnsEmpty_whenNotFound() {
        assertThat(repository.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("findByDisplayName is case-insensitive")
    void findByDisplayName_isCaseInsensitive() {
        repository.save(new Account("user-2", "Bob", Instant.now()));

        assertThat(repository.findByDisplayName("bob")).isPresent();
        assertThat(repository.findByDisplayName("BOB")).isPresent();
        assertThat(repository.findByDisplayName("Bob")).isPresent();
    }

    @Test
    @DisplayName("findAll returns accounts sorted by displayName ascending")
    void findAll_returnsSortedByDisplayName() {
        repository.save(new Account("u1", "Zebra", Instant.now()));
        repository.save(new Account("u2", "Apple", Instant.now()));
        repository.save(new Account("u3", "Mango", Instant.now()));

        var all = repository.findAll();

        assertThat(all).extracting(Account::displayName)
                .containsExactly("Apple", "Mango", "Zebra");
    }

    @Test
    @DisplayName("save creates new account and returns it")
    void save_createsNewAccount() {
        var account = new Account("new-user", "Charlie", Instant.now());
        var saved = repository.save(account);

        assertThat(saved.userId()).isEqualTo("new-user");
        assertThat(repository.findById("new-user")).isPresent();
    }

    @Test
    @DisplayName("save updates existing account by userId")
    void save_updatesExistingAccount() {
        repository.save(new Account("user-x", "Original", Instant.now()));
        repository.save(new Account("user-x", "Updated", Instant.now()));

        var found = repository.findById("user-x");
        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo("Updated");
    }
}
