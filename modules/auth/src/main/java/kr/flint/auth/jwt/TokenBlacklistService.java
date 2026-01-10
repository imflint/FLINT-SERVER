package kr.flint.auth.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "atBlacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    // Access Token을 Blacklist에 추가
    public void blacklist(String accessToken, long remainingTtlSeconds) {
        if (remainingTtlSeconds <= 0) {
            return;
        }
        String key = PREFIX + accessToken;
        stringRedisTemplate.opsForValue().set(key, "1", remainingTtlSeconds, TimeUnit.SECONDS);
    }

    // Blacklist 여부 확인
    public boolean isBlacklisted(String accessToken) {
        String key = PREFIX + accessToken;
        return stringRedisTemplate.hasKey(key);
    }
}
