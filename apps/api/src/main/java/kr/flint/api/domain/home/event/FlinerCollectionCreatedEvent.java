package kr.flint.api.domain.home.event;

/**
 * Fliner가 새 컬렉션을 생성했을 때 발행되는 이벤트
 */
public record FlinerCollectionCreatedEvent(
    Long flinerId,
    Long collectionId
) {}
