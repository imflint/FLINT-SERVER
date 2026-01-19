package kr.flint.api.domain.home.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.flint.api.domain.home.service.CollectionKeywordSyncService;
import kr.flint.collection.event.CollectionContentAddedEvent;
import kr.flint.collection.event.CollectionContentRemovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionContentEventListener {

    private final CollectionKeywordSyncService syncService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentAdded(CollectionContentAddedEvent event) {
        log.debug("콘텐츠 추가 이벤트 수신. id={}, contentId={}", event.collectionId(), event.contentId());

        try {
            syncService.syncOnContentAdded(event.collectionId(), event.contentId());
        } catch (Exception e) {
            log.error("컬렉션 키워드 동기화 실패 (추가). id={}", event.collectionId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentRemoved(CollectionContentRemovedEvent event) {
        log.debug("콘텐츠 삭제 이벤트 수신. id={}, contentId={}", event.collectionId(), event.contentId());
        try {
            syncService.syncOnContentRemoved(event.collectionId(), event.contentId());
        } catch (Exception e) {
            log.error("컬렉션 키워드 동기화 실패 (삭제). id={}", event.collectionId(), e);
        }
    }
}
