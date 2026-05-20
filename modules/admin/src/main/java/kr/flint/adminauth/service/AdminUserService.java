package kr.flint.adminauth.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.adminauth.domain.Admin;
import kr.flint.adminauth.exception.AdminErrorCode;
import kr.flint.adminauth.exception.AdminException;
import kr.flint.adminauth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;

    public Admin getById(Long adminId) {
        return adminUserRepository.findById(adminId)
            .orElseThrow(() -> new AdminException(AdminErrorCode.ADMIN_NOT_FOUND));
    }

    public Optional<Admin> findByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    public boolean canUseAdmin(Long adminId) {
        return adminUserRepository.existsById(adminId);
    }

    public void validateCanUseAdmin(Long adminId) {
        getById(adminId);
    }

    @Transactional
    public Admin updateAccount(Long adminId, String username, String passwordHash) {
        Admin admin = getById(adminId);
        String normalizedUsername = username.trim();

        if (adminUserRepository.existsByUsernameAndIdNot(normalizedUsername, admin.getId())) {
            throw new AdminException(AdminErrorCode.DUPLICATE_ADMIN_USERNAME);
        }

        admin.changeUsername(normalizedUsername);

        if (StringUtils.hasText(passwordHash)) {
            admin.changePassword(passwordHash, LocalDateTime.now());
        }

        return admin;
    }

    @Transactional
    public Optional<Admin> bootstrapAdminIfEmpty(String username, String passwordHash) {
        if (adminUserRepository.count() > 0) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(passwordHash)) {
            return Optional.empty();
        }
        return Optional.of(adminUserRepository.save(Admin.create(username, passwordHash, LocalDateTime.now())));
    }
}
