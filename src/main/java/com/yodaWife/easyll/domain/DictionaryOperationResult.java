package com.yodawife.easyll.domain;

/**
 * Result wrapper for dictionary operations, representing either a successful outcome
 * carrying a value or a failure carrying an error message.
 *
 * @param <T> the type of the successful result value
 */
public sealed interface DictionaryOperationResult<T> {

    /**
     * @return {@code true} when this result represents a successful operation
     */
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    /**
     * Carries the value produced by a successful dictionary operation.
     *
     * @param <T> the type of the result value
     */
    record Success<T>(T value) implements DictionaryOperationResult<T> {}

    /**
     * Carries the error message from a failed dictionary operation.
     *
     * @param <T> the type that would have been returned on success
     */
    record Failure<T>(String errorMessage) implements DictionaryOperationResult<T> {}
}
