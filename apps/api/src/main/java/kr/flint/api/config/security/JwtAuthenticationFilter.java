package kr.flint.api.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = jwtProvider.extractToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (token != null) {
            try {
                // Blacklist 체크
                if (accessTokenBlacklist.isBlacklisted(token)) {
                    log.debug("Blacklisted token detected");
                    // 인증 없이 계속 진행 (인증 필요한 엔드포인트는 403)
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtProvider.isAccessToken(token)) {
                    Long userId = jwtProvider.getUserId(token);
                    String role = jwtProvider.getRole(token);

                    UserPrincipal principal = new UserPrincipal(userId, role);
                    List<SimpleGrantedAuthority> authorities = role != null
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : Collections.emptyList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (GeneralException e) {
                log.debug("JWT 인증 실패: {}", e.getMessage());
                // 인증 실패 시 SecurityContext를 비워두고 계속 진행
                // Public 엔드포인트는 통과하고, 인증이 필요한 엔드포인트는 403 반환
            }
        }

        filterChain.doFilter(request, response);
    }
}
