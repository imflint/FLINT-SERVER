package kr.flint.api.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.flint.api.domain.auth.controller.spec.AuthControllerDocs;
import kr.flint.api.domain.auth.service.AuthFacade;
import kr.flint.api.domain.auth.dto.request.LogoutReq;
import kr.flint.api.domain.auth.dto.request.RefreshTokenReq;
import kr.flint.api.domain.auth.dto.request.SignupReq;
import kr.flint.api.domain.auth.dto.request.SocialVerifyReq;
import kr.flint.api.domain.auth.dto.response.AuthTokenRes;
import kr.flint.api.domain.auth.dto.response.SocialVerifyRes;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthFacade authFacade;
    private final JwtProvider jwtProvider;

    @Override
    @PostMapping("/social/verify")
    public ResponseEntity<SuccessResponse<SocialVerifyRes>> verifySocialCode(@Valid @RequestBody SocialVerifyReq request) {
        return ResponseEntity
                .ok(SuccessResponse.of(SuccessCode.SUCCESS_LOGIN, authFacade.verifySocialCode(request)));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<AuthTokenRes>> signup(@Valid @RequestBody SignupReq request) {
        return ResponseEntity
                .status(SuccessCode.SUCCESS_SIGNUP.getHttpStatus())
                .body(SuccessResponse.of(SuccessCode.SUCCESS_SIGNUP, authFacade.signup(request)));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<AuthTokenRes>> refreshTokens(@Valid @RequestBody RefreshTokenReq request) {
        return ResponseEntity
                .ok(SuccessResponse.of(SuccessCode.SUCCESS_TOKEN_REFRESH, authFacade.refreshTokens(request)));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutReq request,
            HttpServletRequest httpRequest
    ) {
        String accessToken = jwtProvider.extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
        authFacade.logout(accessToken, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/logout/all")
    public ResponseEntity<Void> logoutAll(
            @CurrentUser Long userId,
            HttpServletRequest httpRequest
    ) {
        String accessToken = jwtProvider.extractToken(httpRequest.getHeader(HttpHeaders.AUTHORIZATION));
        authFacade.logoutAll(userId, accessToken);
        return ResponseEntity.noContent().build();
    }
}
