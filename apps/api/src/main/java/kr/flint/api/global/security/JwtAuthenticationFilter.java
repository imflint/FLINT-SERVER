package kr.flint.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.enums.TokenAudience;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.auth.jwt.dto.AccessTokenInfo;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String[] COMMON_EXCLUDED_PATHS = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**"
    };

    private static final String[] POST_EXCLUDED_PATHS = {
            "/api/v1/auth/social/verify",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/dev/login"
    };

    private static final String[] GET_EXCLUDED_PATHS = {
            "/api/v1/terms",
            "/api/v1/terms/**",
            "/api/v1/users/nickname/check",
            "/api/v1/users/{userId:\\d+}",
            "/api/v1/users/{userId:\\d+}/keywords",
            "/api/v1/collections",
            "/api/v1/bookmarks/{collectionId:\\d+}",
            "/api/v1/contents/search",
            "/api/v1/search/contents",
            "/api/v1/home/popular-collections"
    };

    private static final String[] OPTIONAL_AUTH_GET_PATHS = {
            "/api/v1/users/{userId:\\d+}/collections",
            "/api/v1/users/{userId:\\d+}/bookmarked-collections"
    };

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matchesAny(request.getRequestURI(), COMMON_EXCLUDED_PATHS)
            || matchesMethod(request, HttpMethod.POST, POST_EXCLUDED_PATHS)
            || matchesMethod(request, HttpMethod.GET, GET_EXCLUDED_PATHS);
    }

    private boolean matchesMethod(HttpServletRequest request, HttpMethod method, String[] patterns) {
        return method.name().equalsIgnoreCase(request.getMethod()) && matchesAny(request.getRequestURI(), patterns);
    }

    private boolean matchesAny(String path, String[] patterns) {
        for (String pattern : patterns) {
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
        boolean optionalAuthRequest = matchesMethod(request, HttpMethod.GET, OPTIONAL_AUTH_GET_PATHS);

        try {
            authenticate(request);
        } catch (AuthException e) {
            if (!optionalAuthRequest) {
                throw e;
            }
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = jwtProvider.extractToken(authHeader);

        if (token != null) {
            if (accessTokenBlacklist.isBlacklisted(token)) {
                throw new AuthException(AuthErrorCode.TOKEN_BLACKLISTED);
            }

            AccessTokenInfo claims = jwtProvider.parseAccessToken(token);

            if (claims != null && claims.isValid()) {
                if (!claims.isAudience(TokenAudience.USER)) {
                    throw new AuthException(AuthErrorCode.INVALID_TOKEN);
                }
                if (!userService.canUseService(claims.userId())) {
                    throw new AuthException(AuthErrorCode.ACCOUNT_SUSPENDED);
                }
                UserPrincipal principal = new UserPrincipal(claims.userId(), claims.role());
                List<SimpleGrantedAuthority> authorities = claims.role() != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                        : Collections.emptyList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
    }
}
