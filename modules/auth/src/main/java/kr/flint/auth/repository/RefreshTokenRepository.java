package kr.flint.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    // Key 형식: refresh:{userId}:{tokenId}
    private String buildKey(Long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }

    // 패턴 형식: refresh:{userId}:*
    private String buildUserPattern(Long userId) {
        return KEY_PREFIX + userId + ":*";
    }

    // Refresh Token 저장
    public void save(Long userId, String tokenId, String refreshToken, long ttlSeconds) {
        String key = buildKey(userId, tokenId);
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    // Refresh Token 조회
    public Optional<String> findByUserIdAndTokenId(Long userId, String tokenId) {
        String key = buildKey(userId, tokenId);
        Object value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Object::toString);
    }

    // 특정 Refresh Token 삭제
    public void deleteByUserIdAndTokenId(Long userId, String tokenId) {
        String key = buildKey(userId, tokenId);
        redisTemplate.delete(key);
    }

    // 사용자의 모든 Refresh Token 삭제 (전체 로그아웃)
    public void deleteAllByUserId(Long userId) {
        String pattern = buildUserPattern(userId);
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // Refresh Token 존재 여부 확인
    public boolean existsByUserIdAndTokenId(Long userId, String tokenId) {
        String key = buildKey(userId, tokenId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
