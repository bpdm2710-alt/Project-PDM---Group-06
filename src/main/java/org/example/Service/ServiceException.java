package org.example.Service;

/**
 * The only exception the UI has to handle. Services translate SQL and
 * validation problems into one of the {@link Kind}s; {@link #getMessage()}
 * is always safe to show to the user.
 */
public class ServiceException extends Exception {

    public enum Kind {
        /** Input rejected (empty field, bad range, business rule). */
        VALIDATION,
        NOT_FOUND,
        /** Duplicate / concurrent change (already registered, attempts used up). */
        CONFLICT,
        /** Wrong credentials or not allowed to do this. */
        UNAUTHORIZED,
        /** Service not installed or database not reachable. */
        UNAVAILABLE,
        INTERNAL
    }

    private final Kind kind;

    public ServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ServiceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
