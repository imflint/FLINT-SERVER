package kr.flint.collection.event;

/**
 * 컬렉션에서 콘텐츠가 삭제될 때 발행되는 이벤트
 */
public record CollectionContentRemovedEvent(
    Long collectionId,
    Long contentId
) {}
