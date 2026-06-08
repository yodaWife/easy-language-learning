package com.yodawife.easyll.repository.csv;

import com.yodawife.easyll.repository.CsvDictionaryRepository;
import com.yodawife.easyll.repository.DictionaryRepository;
import com.yodawife.easyll.repository.contract.DictionaryRepositoryContractTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "csv"})
class CsvDictionaryRepositoryContractTest extends DictionaryRepositoryContractTest {

    @Autowired
    CsvDictionaryRepository csvDictionaryRepository;

    @Override
    protected DictionaryRepository createRepository() {
        return csvDictionaryRepository;
    }

    @Override
    protected String getTestLanguageCode() {
        return "hun";
    }
}
