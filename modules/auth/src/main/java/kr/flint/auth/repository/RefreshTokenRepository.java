package kr.flint.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.flint.auth.domain.RefreshTokenValue;
import kr.flint.auth.domain.enums.RefreshTokenStatus;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String TOKEN_PREFIX = "rt:";
    private static final String USER_PREFIX = "rtUser:";
    private static final String LOCK_PREFIX = "rtLock:";
    private static final long LOCK_TIMEOUT_SECONDS = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 토큰 저장
    public void save(String token, Long userId, long ttlSeconds) {
        RefreshTokenValue value = RefreshTokenValue.createValid(userId, ttlSeconds);
        String key = buildTokenKey(token);

        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);

            // 사용자별 토큰 목록에 추가
            addToUserTokens(userId, token);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RefreshTokenValue for userId: {}", userId, e);
            throw new AuthException(AuthErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 토큰 정보 조회
    public Optional<RefreshTokenValue> findByToken(String token) {
        String key = buildTokenKey(token);
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        try {
            RefreshTokenValue value = objectMapper.readValue(json, RefreshTokenValue.class);
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize RefreshTokenValue", e);
            return Optional.empty();
        }
    }

    // 상태 변경
    public void updateStatus(String token, RefreshTokenStatus status) {
        findByToken(token).ifPresent(value -> {
            RefreshTokenValue updated = value.withStatus(status);
            String key = buildTokenKey(token);

            try {
                String json = objectMapper.writeValueAsString(updated);
                // 남은 TTL 유지
                Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl > 0) {
                    stringRedisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to update RefreshTokenValue status for token", e);
                throw new AuthException(AuthErrorCode.INTERNAL_SERVER_ERROR);
            }
        });
    }

    // 토큰 삭제
    public void delete(String token, Long userId) {
        String tokenKey = buildTokenKey(token);
        stringRedisTemplate.delete(tokenKey);

        removeFromUserTokens(userId, token);
    }

    // 사용자의 모든 토큰 무효화
    public void revokeAllByUserId(Long userId) {
        String userKey = buildUserKey(userId);
        Set<String> tokens = stringRedisTemplate.opsForSet().members(userKey);

        if (CollectionUtils.isEmpty(tokens)) {
            return;
        }

        for (String token : tokens) {
            updateStatus(token, RefreshTokenStatus.REVOKED);
        }

        log.info("Revoked all refresh tokens for userId: {}", userId);
    }

    // 사용자의 모든 토큰 삭제
    public void deleteAllByUserId(Long userId) {
        String userKey = buildUserKey(userId);
        Set<String> tokens = stringRedisTemplate.opsForSet().members(userKey);

        if (CollectionUtils.isEmpty(tokens)) {
            return;
        }

        for (String token : tokens) {
            String tokenKey = buildTokenKey(token);
            stringRedisTemplate.delete(tokenKey);
        }

        stringRedisTemplate.delete(userKey);
        log.info("Deleted all refresh tokens for userId: {}", userId);
    }

    // 락 획득 시도 (동시 요청 방지)
    public boolean tryLock(String token) {
        String lockKey = buildLockKey(token);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    // 락 해제
    public void unlock(String token) {
        String lockKey = buildLockKey(token);
        stringRedisTemplate.delete(lockKey);
    }

    // Refresh Token 존재 여부 확인
    public boolean exists(String token) {
        String key = buildTokenKey(token);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private String buildTokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String buildUserKey(Long userId) {
        return USER_PREFIX + userId;
    }

    private String buildLockKey(String token) {
        return LOCK_PREFIX + token;
    }

    private void addToUserTokens(Long userId, String token) {
        String userKey = buildUserKey(userId);
        stringRedisTemplate.opsForSet().add(userKey, token);
    }

    private void removeFromUserTokens(Long userId, String token) {
        String userKey = buildUserKey(userId);
        stringRedisTemplate.opsForSet().remove(userKey, token);
    }
}
