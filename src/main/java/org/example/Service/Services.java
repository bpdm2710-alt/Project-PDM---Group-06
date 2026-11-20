package org.example.Service;

/**
 * Where the UI gets its services. Real implementations are installed once at
 * start-up. Until then every getter fails with UNAVAILABLE, so a screen shows
 * its error state instead of silently falling back to fake data.
 */
public final class Services {

    private static volatile AuthService auth;
    private static volatile StudentService student;
    private static volatile InstructorService instructor;

    private Services() {
    }

    public static void install(AuthService a, StudentService s, InstructorService i) {
        auth = a;
        student = s;
        instructor = i;
    }

    public static AuthService auth() throws ServiceException {
        return require(auth, "AuthService");
    }

    public static StudentService student() throws ServiceException {
        return require(student, "StudentService");
    }

    public static InstructorService instructor() throws ServiceException {
        return require(instructor, "InstructorService");
    }

    private static <T> T require(T service, String name) throws ServiceException {
        if (service == null) {
            throw new ServiceException(ServiceException.Kind.UNAVAILABLE,
                    name + " is not available yet. The data layer has not been installed.");
        }
        return service;
    }
}
