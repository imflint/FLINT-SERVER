package kr.flint.auth.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AuthProvider {
    KAKAO,
    APPLE;

    @JsonCreator
    public static AuthProvider from(String value) {
        return AuthProvider.valueOf(value.toUpperCase());
    }
}
