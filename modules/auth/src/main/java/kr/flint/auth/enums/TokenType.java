package kr.flint.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        토큰 타입
        - ACCESS: 접근 토큰 - 인증된 사용자용
        - TEMP: 임시 토큰 - 소셜 인증 후 회원가입 전 상태
        """,
    enumAsRef = true
)
public enum TokenType {
    ACCESS,
    TEMP
}
