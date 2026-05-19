package kr.flint.api.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.flint.user.service.DailyVisitorActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyVisitorActivityFilter extends OncePerRequestFilter {

    private static final String UNKNOWN_USER_AGENT = "unknown";
    private static final String[] EXCLUDED_PATHS = {
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/api/v1/auth/**",
        "/api/v1/terms",
        "/api/v1/terms/**",
        "/api/v1/users/nickname/check",
        "/actuator/**"
    };

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final DailyVisitorActivityService dailyVisitorActivityService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        for (String pattern : EXCLUDED_PATHS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Long userId = currentUserId();
            dailyVisitorActivityService.recordVisit(visitorKeyHash(request, userId), userId);
        } catch (RuntimeException exception) {
            log.warn("사용자 접속 기록 저장 실패: {}", exception.getMessage(), exception);
        }

        filterChain.doFilter(request, response);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal.userId();
    }

    private String visitorKeyHash(HttpServletRequest request, Long userId) {
        if (userId != null) {
            return sha256("user:" + userId);
        }
        return sha256("guest:" + clientIp(request) + "|" + userAgent(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return userAgent == null || userAgent.isBlank() ? UNKNOWN_USER_AGENT : userAgent.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
