package kr.flint.admin.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.admin.domain.account.dto.request.AdminAccountUpdateReq;
import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.service.AdminUserService;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;

@ExtendWith(MockitoExtension.class)
class AdminAccountFacadeTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminAccountFacade facade;

    @Test
    @DisplayName("관리자 본인 정보를 조회")
    void getMe() {
        Admin admin = admin("admin", "hash");
        when(adminUserService.getById(1L)).thenReturn(admin);

        var result = facade.getMe(1L);

        assertThat(result.adminId()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 아이디와 비밀번호를 수정")
    void updateMe() {
        Admin admin = admin("admin", "old-hash");
        Admin updatedAdmin = admin("operator", "new-hash");
        AdminAccountUpdateReq request = new AdminAccountUpdateReq("operator", "current-password", "new-password");
        when(adminUserService.getById(1L)).thenReturn(admin);
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(adminUserService.updateAccount(1L, "operator", "new-hash")).thenReturn(updatedAdmin);

        var result = facade.updateMe(1L, request);

        assertThat(result.username()).isEqualTo("operator");
        verify(adminUserService).updateAccount(1L, "operator", "new-hash");
    }

    @Test
    @DisplayName("새 비밀번호가 없으면 아이디만 수정")
    void updateMeWithoutNewPassword() {
        Admin admin = admin("admin", "old-hash");
        Admin updatedAdmin = admin("operator", "old-hash");
        AdminAccountUpdateReq request = new AdminAccountUpdateReq("operator", "current-password", null);
        when(adminUserService.getById(1L)).thenReturn(admin);
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(adminUserService.updateAccount(1L, "operator", null)).thenReturn(updatedAdmin);

        var result = facade.updateMe(1L, request);

        assertThat(result.username()).isEqualTo("operator");
        verify(passwordEncoder, never()).encode("new-password");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 수정하지 않음")
    void invalidCurrentPassword() {
        Admin admin = admin("admin", "old-hash");
        AdminAccountUpdateReq request = new AdminAccountUpdateReq("operator", "wrong-password", "new-password");
        when(adminUserService.getById(1L)).thenReturn(admin);
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> facade.updateMe(1L, request))
            .isInstanceOf(AuthException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(adminUserService, never()).updateAccount(1L, "operator", "new-password");
    }

    private Admin admin(String username, String passwordHash) {
        Admin admin = Admin.create(username, passwordHash, LocalDateTime.now());
        ReflectionTestUtils.setField(admin, "id", 1L);
        return admin;
    }
}
