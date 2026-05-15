package kr.flint.admin.domain.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import kr.flint.admin.domain.auth.properties.AdminAuthProperties;
import kr.flint.adminauth.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthBootstrapRunner implements ApplicationRunner {

    private final AdminAuthProperties adminAuthProperties;
    private final AdminUserService adminUserService;

    @Override
    public void run(ApplicationArguments args) {
        adminUserService.bootstrapAdminIfEmpty(
            adminAuthProperties.username(),
            adminAuthProperties.passwordHash()
        ).ifPresent(admin -> log.info("초기 관리자 계정이 생성되었습니다. adminId: {}", admin.getId()));
    }
}
