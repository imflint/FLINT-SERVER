package kr.flint.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        사용자 계정 상태
        - ACTIVE: 활성 계정 - 정상 이용 가능
        - WITHDRAWN: 탈퇴 계정 - 서비스 이용 불가 -> 미사용
        """,
    enumAsRef = true
)
public enum UserStatus {
    ACTIVE,
    WITHDRAWN
}
