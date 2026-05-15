package kr.flint.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.flint.auth.dto.RefreshTokenValue;
import kr.flint.auth.enums.RefreshTokenStatus;
import kr.flint.auth.enums.TokenAudience;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String TOKEN_PREFIX = "rt:";
    private static final String TOKEN_LOOKUP_PREFIX = "rtKey:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 토큰 생성 (UUID)
    public String createToken() {
        return UUID.randomUUID().toString();
    }

    // rt:{audience}:{userId}:{token} = 데이터, rtKey:{token} = audience:userId
    public void save(String token, Long userId, long ttlSeconds) {
        save(token, userId, TokenAudience.USER, ttlSeconds);
    }

    public void save(String token, Long userId, TokenAudience audience, long ttlSeconds) {
        RefreshTokenValue value = RefreshTokenValue.createValid(userId, audience, ttlSeconds);
        String dataKey = buildDataKey(audience, userId, token);
        String lookupKey = buildLookupKey(token);

        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(dataKey, json, ttlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(lookupKey, buildLookupValue(audience, userId), ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("RefreshTokenValue 직렬화 실패, userId: {}", userId, e);
            throw new AuthException(AuthErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 토큰 정보 조회 (토큰으로만 조회)
    public Optional<RefreshTokenValue> findByToken(String token) {
        String lookupKey = buildLookupKey(token);
        String userIdStr = stringRedisTemplate.opsForValue().get(lookupKey);

        if (userIdStr == null) {
            return Optional.empty();
        }

        TokenLookup tokenLookup = parseLookup(userIdStr);
        String dataKey = buildDataKey(tokenLookup, token);
        String json = stringRedisTemplate.opsForValue().get(dataKey);

        if (json == null) {
            return Optional.empty();
        }

        try {
            RefreshTokenValue value = objectMapper.readValue(json, RefreshTokenValue.class);
            return Optional.of(value.withAudienceIfMissing(tokenLookup.audience()));
        } catch (JsonProcessingException e) {
            log.error("RefreshTokenValue 역직렬화 실패", e);
            return Optional.empty();
        }
    }

    // 상태 변경
    public void updateStatus(String token, RefreshTokenStatus status) {
        String lookupKey = buildLookupKey(token);
        String userIdStr = stringRedisTemplate.opsForValue().get(lookupKey);

        if (userIdStr == null) {
            return;
        }

        TokenLookup tokenLookup = parseLookup(userIdStr);
        String dataKey = buildDataKey(tokenLookup, token);

        findByToken(token).ifPresent(value -> {
            RefreshTokenValue updated = value.withStatus(status);

            try {
                String json = objectMapper.writeValueAsString(updated);
                Long ttl = stringRedisTemplate.getExpire(dataKey, TimeUnit.SECONDS);
                if (ttl != null && ttl > 0) {
                    stringRedisTemplate.opsForValue().set(dataKey, json, ttl, TimeUnit.SECONDS);
                }
            } catch (JsonProcessingException e) {
                log.error("RefreshTokenValue 상태 변경 실패", e);
                throw new AuthException(AuthErrorCode.INTERNAL_SERVER_ERROR);
            }
        });
    }

    // VALID 상태인 경우에만 USED로 원자적 변경
    private static final String MARK_AS_USED_SCRIPT = """
            local userId = redis.call('GET', KEYS[2])
            if not userId then
                return nil
            end
            local dataKey = ARGV[1] .. userId .. ':' .. ARGV[2]
            local current = redis.call('GET', dataKey)
            if not current then
                return nil
            end
            if string.find(current, '"status":"VALID"') then
                local updated = string.gsub(current, '"status":"VALID"', '"status":"USED"')
                local ttl = redis.call('TTL', dataKey)
                if ttl > 0 then
                    redis.call('SETEX', dataKey, ttl, updated)
                end
                return current
            end
            return current
            """;

    public Optional<RefreshTokenValue> markAsUsedIfValid(String token) {
        String lookupKey = buildLookupKey(token);
        String lookupValue = stringRedisTemplate.opsForValue().get(lookupKey);
        if (lookupValue == null) {
            return Optional.empty();
        }
        TokenLookup tokenLookup = parseLookup(lookupValue);

        String result = stringRedisTemplate.execute(
                new DefaultRedisScript<>(MARK_AS_USED_SCRIPT, String.class),
                List.of("dummy", lookupKey),
                TOKEN_PREFIX, token
        );

        if (result == null) {
            return Optional.empty();
        }

        try {
            RefreshTokenValue value = objectMapper.readValue(result, RefreshTokenValue.class);
            return Optional.of(value.withAudienceIfMissing(tokenLookup.audience()));
        } catch (JsonProcessingException e) {
            log.error("RefreshTokenValue 역직렬화 실패", e);
            return Optional.empty();
        }
    }

    // 토큰 삭제
    public void delete(String token, Long userId) {
        String lookupKey = buildLookupKey(token);
        String lookupValue = stringRedisTemplate.opsForValue().get(lookupKey);
        String dataKey = lookupValue != null ? buildDataKey(parseLookup(lookupValue), token) : buildLegacyDataKey(userId, token);
        stringRedisTemplate.delete(List.of(dataKey, lookupKey));
    }

    // 사용자의 모든 토큰 무효화 (SCAN 사용)
    public void revokeAllByUserId(Long userId) {
        List<String> tokenKeys = scanKeysByUserId(userId);

        for (String dataKey : tokenKeys) {
            String token = extractTokenFromDataKey(dataKey);
            updateStatus(token, RefreshTokenStatus.REVOKED);
        }

        if (!tokenKeys.isEmpty()) {
            log.debug("{}개의 리프레시 토큰 무효화 완료, userId: {}", tokenKeys.size(), userId);
        }
    }

    // 사용자의 모든 토큰 삭제 (SCAN 사용)
    public void deleteAllByUserId(Long userId) {
        List<String> dataKeys = scanKeysByUserId(userId);

        if (dataKeys.isEmpty()) {
            return;
        }

        List<String> allKeys = new ArrayList<>(dataKeys);
        for (String dataKey : dataKeys) {
            String token = extractTokenFromDataKey(dataKey);
            allKeys.add(buildLookupKey(token));
        }

        stringRedisTemplate.delete(allKeys);
        log.debug("{}개의 리프레시 토큰 삭제 완료, userId: {}", dataKeys.size(), userId);
    }

    public void deleteAllBySubject(TokenAudience audience, Long userId) {
        List<String> dataKeys = scanKeysBySubject(audience, userId);

        if (dataKeys.isEmpty()) {
            return;
        }

        List<String> allKeys = new ArrayList<>(dataKeys);
        for (String dataKey : dataKeys) {
            String token = extractTokenFromDataKey(dataKey);
            allKeys.add(buildLookupKey(token));
        }

        stringRedisTemplate.delete(allKeys);
        log.debug("{}개의 리프레시 토큰 삭제 완료, audience: {}, userId: {}", dataKeys.size(), audience, userId);
    }

    // Refresh Token 존재 여부 확인
    public boolean exists(String token) {
        String lookupKey = buildLookupKey(token);
        return stringRedisTemplate.hasKey(lookupKey);
    }

    // rt:{audience}:{userId}:{token}
    private String buildDataKey(TokenAudience audience, Long userId, String token) {
        return TOKEN_PREFIX + audience.name() + ":" + userId + ":" + token;
    }

    private String buildDataKey(TokenLookup tokenLookup, String token) {
        return TOKEN_PREFIX + tokenLookup.rawValue() + ":" + token;
    }

    // legacy: rt:{userId}:{token}
    private String buildLegacyDataKey(Long userId, String token) {
        return TOKEN_PREFIX + userId + ":" + token;
    }

    // rtKey:{token}
    private String buildLookupKey(String token) {
        return TOKEN_LOOKUP_PREFIX + token;
    }

    private String buildLookupValue(TokenAudience audience, Long userId) {
        return audience.name() + ":" + userId;
    }

    // SCAN으로 userId의 모든 토큰 키 조회
    private List<String> scanKeysByUserId(Long userId) {
        List<String> keys = new ArrayList<>();
        keys.addAll(scanKeys(TOKEN_PREFIX + userId + ":*"));
        keys.addAll(scanKeys(TOKEN_PREFIX + TokenAudience.USER.name() + ":" + userId + ":*"));
        return keys;
    }

    private List<String> scanKeysBySubject(TokenAudience audience, Long userId) {
        return scanKeys(TOKEN_PREFIX + audience.name() + ":" + userId + ":*");
    }

    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();

        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }

        return keys;
    }

    private TokenLookup parseLookup(String lookupValue) {
        int separatorIndex = lookupValue.indexOf(':');
        if (separatorIndex < 0) {
            return new TokenLookup(TokenAudience.USER, Long.valueOf(lookupValue), lookupValue);
        }
        TokenAudience audience = TokenAudience.valueOf(lookupValue.substring(0, separatorIndex));
        Long userId = Long.valueOf(lookupValue.substring(separatorIndex + 1));
        return new TokenLookup(audience, userId, lookupValue);
    }

    // rt:{userId}:{token}에서 token 추출
    private String extractTokenFromDataKey(String dataKey) {
        int lastColonIndex = dataKey.lastIndexOf(':');
        return dataKey.substring(lastColonIndex + 1);
    }

    private record TokenLookup(
        TokenAudience audience,
        Long userId,
        String rawValue
    ) {
    }
}
