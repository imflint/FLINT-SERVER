package kr.flint.admin.domain.user.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.user.dto.response.AdminUserStatisticsRes;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserStatisticsFacade {

    private final AdminAuthorizationService adminAuthorizationService;
    private final UserService userService;

    public AdminUserStatisticsRes getStatistics(Long adminId) {
        adminAuthorizationService.validateAdmin(adminId);
        return AdminUserStatisticsRes.of(userService.countActiveUsers());
    }
}
