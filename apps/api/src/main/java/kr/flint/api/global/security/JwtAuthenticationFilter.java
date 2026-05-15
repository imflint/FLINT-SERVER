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

    private static final String[] EXCLUDED_PATHS = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/social/verify",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/terms",
            "/api/v1/terms/**",
            "/api/v1/users/nickname/check",
            "/actuator/**"
    };

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
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

        filterChain.doFilter(request, response);
    }
}
