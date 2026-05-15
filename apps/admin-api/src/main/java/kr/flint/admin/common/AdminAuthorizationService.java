package kr.flint.admin.common;

import org.springframework.stereotype.Component;

import kr.flint.adminauth.service.AdminUserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private final AdminUserService adminUserService;

    public void validateAdmin(Long adminId) {
        adminUserService.validateCanUseAdmin(adminId);
    }
}
