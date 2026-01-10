package kr.flint.api.user.service;

import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCommandFacade {

    private final UserService userService;

    // TODO: 프로필 수정, 회원 탈퇴 등 Command 작업 추가 예정
}
