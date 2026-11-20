package org.example.session;

import org.example.Service.AuthService.UserAccount;


public final class Session {

    private static volatile UserAccount current;

    private Session() {
    }

    public static void start(UserAccount user) {
        current = user;
    }

    public static void end() {
        current = null;
    }

    public static boolean isLoggedIn() {
        return current != null;
    }

    public static UserAccount user() {
        UserAccount u = current;
        if (u == null) {
            throw new IllegalStateException("No user is logged in");
        }
        return u;
    }
}
