package kr.flint.api.config.security;

import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;

import java.security.Principal;

public record UserPrincipal(
        Long userId,
        String role
) implements Principal {

    public UserPrincipal {
        if (userId == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
