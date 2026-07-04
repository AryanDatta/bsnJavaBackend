package com.bsn.backend.social.common;

/**
 * Container for lightweight API exceptions used by the social module.
 * Each maps to a status via @ResponseStatus so the existing GlobalExceptionHandler
 * does not need changes (its generic handler is bypassed by ResponseStatusException semantics).
 */
public final class ApiExceptions {
    private ApiExceptions() {
    }
}
