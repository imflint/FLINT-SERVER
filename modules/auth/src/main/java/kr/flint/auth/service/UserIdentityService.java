package kr.flint.auth.service;

import kr.flint.auth.enums.AuthProvider;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIdentityService {

    private final UserIdentityRepository userIdentityRepository;

    public Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    @Transactional
    public UserIdentity create(Long userId, AuthProvider provider, String providerUserId) {
        try {
            return userIdentityRepository.save(UserIdentity.create(userId, provider, providerUserId));
        } catch (DataIntegrityViolationException e) {
            if (userIdentityRepository.existsByProviderAndProviderUserId(provider, providerUserId)) {
                throw new AuthException(AuthErrorCode.DUPLICATE_IDENTITY);
            }
            throw e;
        }
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        userIdentityRepository.deleteAllByUserId(userId);
    }
}
