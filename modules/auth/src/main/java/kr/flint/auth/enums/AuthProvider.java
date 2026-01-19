package kr.flint.auth.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        소셜 로그인 제공자
        - KAKAO: 카카오 로그인
        - APPLE: 애플 로그인
        """,
    enumAsRef = true
)
public enum AuthProvider {
    KAKAO,
    APPLE;

    @JsonCreator
    public static AuthProvider from(String value) {
        return AuthProvider.valueOf(value.toUpperCase());
    }
}
