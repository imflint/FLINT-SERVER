package kr.flint.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 역할", enumAsRef = true)
public enum UserRole {
    @Schema(description = "관리자 - 시스템 전체 관리 권한")
    ADMIN,

    @Schema(description = "일반 사용자 - 컬렉션 생성 및 관리 가능")
    FLINER,

    @Schema(description = "게스트 사용자 - 조회만 가능")
    FLING
}
