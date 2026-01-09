package kr.flint.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    // Key: refresh:{token}
    private String buildKey(String token) {
        return KEY_PREFIX + token;
    }

    private String buildAllPattern() {
        return KEY_PREFIX + "*";
    }

    public void save(String token, Long userId, long ttlSeconds) {
        String key = buildKey(token);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
    }

    public Optional<Long> findUserIdByToken(String token) {
        String key = buildKey(token);
        String value = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    public void delete(String token) {
        String key = buildKey(token);
        stringRedisTemplate.delete(key);
    }

    // 사용자의 모든 Refresh Token 삭제 (전체 로그아웃)
    public void deleteAllByUserId(Long userId) {
        Set<String> keys = stringRedisTemplate.keys(buildAllPattern());
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }

        String userIdStr = String.valueOf(userId);
        for (String key : keys) {
            String storedUserId = stringRedisTemplate.opsForValue().get(key);
            if (userIdStr.equals(storedUserId)) {
                stringRedisTemplate.delete(key);
            }
        }
    }

    // Refresh Token 존재 여부 확인
    public boolean exists(String token) {
        String key = buildKey(token);
        return stringRedisTemplate.hasKey(key);
    }
}
