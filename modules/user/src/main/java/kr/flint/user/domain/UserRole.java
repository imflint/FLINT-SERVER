package kr.flint.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        사용자 역할
        - FLINER: 플리너 - 컬렉션 생성 및 관리 가능
        - FLING: 일반회원
        """,
    enumAsRef = true
)
public enum UserRole {
    FLINER,
    FLING
}
