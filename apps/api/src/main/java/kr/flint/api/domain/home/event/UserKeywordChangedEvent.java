package kr.flint.api.domain.home.event;

/**
 * 사용자 취향 키워드 변경 시 발행되는 이벤트
 */
public record UserKeywordChangedEvent(
    Long userId
) {}
