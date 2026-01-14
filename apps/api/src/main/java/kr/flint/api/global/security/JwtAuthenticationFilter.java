package kr.flint.api.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.auth.jwt.dto.AccessTokenInfo;
import kr.flint.auth.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = jwtProvider.extractToken(authHeader);

        if (token != null) {
            if (accessTokenBlacklist.isBlacklisted(token)) {
                throw new AuthException(AuthErrorCode.TOKEN_BLACKLISTED);
            }

            AccessTokenInfo claims = jwtProvider.parseAccessToken(token);

            if (claims != null && claims.isValid()) {
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
