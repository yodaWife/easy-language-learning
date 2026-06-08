package com.yodawife.easyll.repository.csv;

import com.yodawife.easyll.repository.AccountRepository;
import com.yodawife.easyll.repository.CsvAccountRepository;
import com.yodawife.easyll.repository.contract.AccountRepositoryContractTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;

class CsvAccountRepositoryContractTest extends AccountRepositoryContractTest {

    @TempDir
    @Nullable Path tempDir;

    @Override
    protected AccountRepository createRepository() {
        var accountsFile = Objects.requireNonNull(tempDir).resolve("users.csv");
        return new CsvAccountRepository(accountsFile.toString());
    }
}
