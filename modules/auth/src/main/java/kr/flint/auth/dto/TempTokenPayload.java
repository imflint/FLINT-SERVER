package kr.flint.auth.dto;

import kr.flint.auth.enums.AuthProvider;

public record TempTokenPayload(
        AuthProvider provider,
        String providerUserId
) {}
