package org.example.Service;

public interface AuthService {

    enum Role { STUDENT, INSTRUCTOR }

    record UserAccount(int userId, String firstName, String lastName,
                       String email, Role role) {
        public String fullName() {
            return (firstName + " " + lastName).trim();
        }
    }

    /**
     * @throws ServiceException UNAUTHORIZED when email/password do not match
     *                          (never say which one is wrong).
     */
    UserAccount login(String email, String password) throws ServiceException;
}
