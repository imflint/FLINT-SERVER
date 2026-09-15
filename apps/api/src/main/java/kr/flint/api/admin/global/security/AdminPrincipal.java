package kr.flint.api.admin.global.security;

import java.security.Principal;

public record AdminPrincipal(
    Long adminId
) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(adminId);
    }
}
