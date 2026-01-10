package kr.flint.api.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.flint.api.auth.controller.spec.AuthControllerDocs;
import kr.flint.api.auth.service.AuthFacade;
import kr.flint.api.config.security.UserPrincipal;
import kr.flint.auth.dto.request.LogoutRequest;
import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthFacade authFacade;
    private final JwtProvider jwtProvider;

    @Override
    @PostMapping("/social/verify")
    public ResponseEntity<SuccessResponse<SocialVerifyResponse>> verifySocialCode(@Valid @RequestBody SocialVerifyRequest request) {
        return ResponseEntity
                .ok(SuccessResponse.of(SuccessCode.SUCCESS_LOGIN, authFacade.verifySocialCode(request)));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<AuthTokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity
                .status(SuccessCode.SUCCESS_SIGNUP.getHttpStatus())
                .body(SuccessResponse.of(SuccessCode.SUCCESS_SIGNUP, authFacade.signup(request)));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<AuthTokenResponse>> refreshTokens(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity
                .ok(SuccessResponse.of(SuccessCode.SUCCESS_TOKEN_REFRESH, authFacade.refreshTokens(request)));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest
    ) {
        String accessToken = jwtProvider.extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
        authFacade.logout(accessToken, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/logout/all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        String accessToken = jwtProvider.extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
        authFacade.logoutAll(principal.userId(), accessToken);
        return ResponseEntity.noContent().build();
    }
}
