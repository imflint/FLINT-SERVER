package kr.flint.api.auth.controller;

import jakarta.validation.Valid;
import kr.flint.api.auth.controller.spec.AuthControllerDocs;
import kr.flint.api.auth.service.AuthFacade;
import kr.flint.api.config.security.UserPrincipal;
import kr.flint.auth.dto.request.LogoutRequest;
import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.NicknameCheckResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthFacade authFacade;

    @Override
    @PostMapping("/social/verify")
    public ResponseEntity<SuccessResponse<SocialVerifyResponse>> verifySocialCode(@Valid @RequestBody SocialVerifyRequest request) {
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_LOGIN, authFacade.verifySocialCode(request)));
    }

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, authFacade.checkNickname(nickname)));
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<AuthTokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.of(SuccessCode.SUCCESS_SIGNUP, authFacade.signup(request)));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<AuthTokenResponse>> refreshTokens(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_TOKEN_REFRESH, authFacade.refreshTokens(request)));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) LogoutRequest request
    ) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authFacade.logout(principal.userId(), refreshToken);
        return ResponseEntity.noContent().build();
    }
}
