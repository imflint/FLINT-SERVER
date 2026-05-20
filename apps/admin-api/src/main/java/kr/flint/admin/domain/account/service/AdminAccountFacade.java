package kr.flint.admin.domain.account.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.admin.domain.account.dto.request.AdminAccountUpdateReq;
import kr.flint.admin.domain.account.dto.response.AdminMeRes;
import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.service.AdminUserService;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAccountFacade {

    private final AdminUserService adminUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminMeRes getMe(Long adminId) {
        return AdminMeRes.from(adminUserService.getById(adminId));
    }

    @Transactional
    public AdminMeRes updateMe(Long adminId, AdminAccountUpdateReq request) {
        Admin admin = adminUserService.getById(adminId);

        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String newPasswordHash = StringUtils.hasText(request.newPassword())
            ? passwordEncoder.encode(request.newPassword())
            : null;
        Admin updatedAdmin = adminUserService.updateAccount(adminId, request.username(), newPasswordHash);

        return AdminMeRes.from(updatedAdmin);
    }
}
