package com.yodawife.easyll.domain;

import java.util.List;

public sealed interface CsvParseResult<T> permits CsvParseResult.Success, CsvParseResult.Failure {

    record Success<T>(T value) implements CsvParseResult<T> {}

    record Failure<T>(List<String> errors) implements CsvParseResult<T> {
        public Failure {
            errors = List.copyOf(errors);
        }
    }
}
