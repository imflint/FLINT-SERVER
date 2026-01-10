package kr.flint.auth.domain.enums;

public enum RefreshTokenStatus {
    VALID,    // 유효한 토큰 (사용 가능)
    USED,     // 갱신에 사용됨 (재사용 시 전체 무효화)
    REVOKED   // 로그아웃/보안 이벤트로 무효화
}
