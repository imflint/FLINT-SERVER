package kr.flint.api.admin.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.flint.adminauth.service.AdminUserService;
import kr.flint.api.admin.global.security.AdminJwtAuthenticationFilter;
import kr.flint.api.admin.global.security.AdminJwtExceptionFilter;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.shared.exception.AppError;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.ProblemDetail;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public AdminJwtAuthenticationFilter adminJwtAuthenticationFilter(
        JwtProvider jwtProvider,
        AccessTokenBlacklist accessTokenBlacklist,
        AdminUserService adminUserService
    ) {
        return new AdminJwtAuthenticationFilter(jwtProvider, accessTokenBlacklist, adminUserService);
    }

    @Bean
    public AdminJwtExceptionFilter adminJwtExceptionFilter(ObjectMapper objectMapper) {
        return new AdminJwtExceptionFilter(objectMapper);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(
        HttpSecurity http,
        AdminJwtAuthenticationFilter adminJwtAuthenticationFilter,
        AdminJwtExceptionFilter adminJwtExceptionFilter
    ) throws Exception {
        return http
            .securityMatcher("/api/v1/admin/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> { })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, ex) ->
                    writeErrorResponse(response, request, AuthErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((request, response, ex) ->
                    writeErrorResponse(response, request, ErrorCode.FORBIDDEN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/admin/auth/login",
                    "/api/v1/admin/auth/refresh"
                ).permitAll()
                .anyRequest().hasRole("ADMIN"))
            .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(adminJwtExceptionFilter, AdminJwtAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeErrorResponse(
        HttpServletResponse response,
        HttpServletRequest request,
        AppError error
    ) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.of(error, request.getRequestURI());
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
