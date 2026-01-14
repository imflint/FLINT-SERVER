package kr.flint.collection.event;

/**
 * 컬렉션에 콘텐츠가 추가될 때 발행되는 이벤트
 */
public record CollectionContentAddedEvent(
    Long collectionId,
    Long contentId
) {}
