package kr.flint.api.auth.controller;

import jakarta.validation.Valid;
import kr.flint.api.auth.controller.spec.AuthControllerDocs;
import kr.flint.api.auth.facade.AuthFacade;
import kr.flint.api.config.security.UserPrincipal;
import kr.flint.auth.dto.request.LogoutRequest;
import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.NicknameCheckResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthFacade authFacade;

    @Override
    @PostMapping("/social/verify")
    public SocialVerifyResponse verifySocialToken(@Valid @RequestBody SocialVerifyRequest request) {
        return authFacade.verifySocialToken(request);
    }

    @Override
    @GetMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(@RequestParam String nickname) {
        return authFacade.checkNickname(nickname);
    }

    @Override
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return authFacade.signup(request);
    }

    @Override
    @PostMapping("/refresh")
    public AuthTokenResponse refreshTokens(@Valid @RequestBody RefreshTokenRequest request) {
        return authFacade.refreshTokens(request);
    }

    @Override
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) LogoutRequest request
    ) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authFacade.logout(principal.userId(), refreshToken);
    }
}
