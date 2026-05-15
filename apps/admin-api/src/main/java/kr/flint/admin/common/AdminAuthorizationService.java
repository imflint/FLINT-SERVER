package kr.flint.admin.common;

import org.springframework.stereotype.Component;

import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import kr.flint.user.domain.UserRole;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private final UserService userService;

    public void validateAdmin(Long userId) {
        UserAuthInfo authInfo = userService.getAuthInfo(userId);
        if (!UserRole.ADMIN.name().equals(authInfo.role())) {
            throw new GeneralException(ErrorCode.FORBIDDEN);
        }
    }
}
