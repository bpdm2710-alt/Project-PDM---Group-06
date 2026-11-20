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

    /** Short "ExceptionType: message" of the deepest cause, for dev-time error messages. */
    public static String rootCauseText(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        String m = r.getMessage() == null ? "" : r.getMessage().replaceAll("\\s+", " ").trim();
        if (m.length() > 220) {
            m = m.substring(0, 220) + "...";
        }
        return r.getClass().getSimpleName() + (m.isEmpty() ? "" : ": " + m);
    }
}
