package kr.flint.api.domain.home.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.flint.api.domain.home.service.RecommendationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 추천 캐시 무효화 이벤트 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationCacheEventListener {

    private final RecommendationCacheService cacheService;

    // 사용자 키워드 변경 시 해당 사용자 캐시만 무효화
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserKeywordChanged(UserKeywordChangedEvent event) {
        log.debug("사용자 키워드 변경 이벤트 수신. userId={}", event.userId());
        try {
            cacheService.invalidateUserCache(event.userId());
        } catch (Exception e) {
            log.error("사용자 캐시 무효화 실패. userId={}", event.userId(), e);
        }
    }

    // Fliner 컬렉션 생성 시 전체 캐시 무효화
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlinerCollectionCreated(FlinerCollectionCreatedEvent event) {
        log.debug("Fliner 컬렉션 생성 이벤트 수신. flinerId={}, id={}", event.flinerId(), event.collectionId());
        try {
            cacheService.invalidateAllCache();
        } catch (Exception e) {
            log.error("전체 캐시 무효화 실패.", e);
        }
    }
}
