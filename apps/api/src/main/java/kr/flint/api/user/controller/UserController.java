package kr.flint.api.user.controller;

import kr.flint.api.user.controller.spec.UserControllerDocs;
import kr.flint.api.user.service.UserQueryFacade;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.dto.response.NicknameCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserQueryFacade userQueryFacade;

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam String nickname
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, userQueryFacade.checkNickname(nickname))
        );
    }
}
