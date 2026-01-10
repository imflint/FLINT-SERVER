package kr.flint.auth.dto.response;

import kr.flint.auth.domain.enums.AuthProvider;

public record TempTokenPayload(
        AuthProvider provider,
        String providerUserId
) {}
