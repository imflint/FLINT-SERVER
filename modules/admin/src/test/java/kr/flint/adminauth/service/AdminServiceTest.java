package kr.flint.adminauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.exception.AdminErrorCode;
import kr.flint.adminauth.exception.AdminException;
import kr.flint.adminauth.repository.AdminUserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    @Nested
    @DisplayName("bootstrap")
    class Bootstrap {

        @Test
        @DisplayName("관리자 계정이 없으면 초기 관리자 계정을 생성")
        void createAdminIfEmpty() {
            when(adminUserRepository.count()).thenReturn(0L);
            when(adminUserRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Optional<Admin> result = adminUserService.bootstrapAdminIfEmpty("admin", "hash");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("admin");
            verify(adminUserRepository).save(any(Admin.class));
        }

        @Test
        @DisplayName("관리자 계정이 있으면 bootstrap 계정을 생성하지 않음")
        void skipIfAdminExists() {
            when(adminUserRepository.count()).thenReturn(1L);

            Optional<Admin> result = adminUserService.bootstrapAdminIfEmpty("admin", "hash");

            assertThat(result).isEmpty();
            verify(adminUserRepository, never()).save(any(Admin.class));
        }
    }

    @Nested
    @DisplayName("validateCanUseAdmin")
    class ValidateCanUseAdmin {

        @Test
        @DisplayName("존재하지 않는 관리자는 사용할 수 없음")
        void notFound() {
            when(adminUserRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.validateCanUseAdmin(1L))
                .isInstanceOf(AdminException.class)
                .extracting("errorCode")
                .isEqualTo(AdminErrorCode.ADMIN_NOT_FOUND);
        }
    }
}
