package kr.flint.api.domain.home.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 추천 컬렉션 캐시 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "recommendation:user:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    // 캐시 조회
    public Optional<List<Long>> getCachedRecommendations(Long userId) {
        try {
            String key = buildKey(userId);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?> list) {
                List<Long> result = list.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::longValue)
                    .toList();
                return result.isEmpty() ? Optional.empty() : Optional.of(result);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("캐시 조회 실패. userId={}", userId, e);
            return Optional.empty();
        }
    }

    // 캐시 저장
    public void cacheRecommendations(Long userId, List<Long> collectionIds) {
        try {
            String key = buildKey(userId);
            redisTemplate.opsForValue().set(key, collectionIds, DEFAULT_TTL);
            log.debug("캐시 저장 완료. userId={}, count={}", userId, collectionIds.size());
        } catch (Exception e) {
            log.warn("캐시 저장 실패. userId={}", userId, e);
        }
    }

    // 단일 사용자 캐시 무효화
    public void invalidateUserCache(Long userId) {
        try {
            String key = buildKey(userId);
            Boolean deleted = redisTemplate.delete(key);
            log.debug("사용자 캐시 무효화. userId={}, deleted={}", userId, deleted);
        } catch (Exception e) {
            log.warn("사용자 캐시 무효화 실패. userId={}", userId, e);
        }
    }

    // 전체 캐시 무효화
    public void invalidateAllCache() {
        try {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(CACHE_PREFIX + "*")
                .count(100)
                .build();

            List<String> keysToDelete = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }

            if (!keysToDelete.isEmpty()) {
                Long deleted = redisTemplate.delete(keysToDelete);
                log.info("전체 캐시 무효화. deleted={}", deleted);
            }
        } catch (Exception e) {
            log.warn("전체 캐시 무효화 실패.", e);
        }
    }

    // 순서 셔플 (앱 재실행 시)
    public List<Long> getShuffledOrder(List<Long> collectionIds) {
        List<Long> shuffled = new java.util.ArrayList<>(collectionIds);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    private String buildKey(Long userId) {
        return CACHE_PREFIX + userId;
    }
}
