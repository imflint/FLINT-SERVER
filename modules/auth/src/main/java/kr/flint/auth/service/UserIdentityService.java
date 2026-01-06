package kr.flint.auth.service;

import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserIdentityService {

    private final UserIdentityRepository userIdentityRepository;

    /**
     * Retrieve the user identity for a given authentication provider and provider-specific user identifier.
     *
     * @param provider the authentication provider
     * @param providerUserId the identifier assigned to the user by the provider
     * @return an {@code Optional<UserIdentity]} containing the matching UserIdentity if present, {@code Optional.empty()} otherwise
     */
    public Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    /**
     * Creates and persists a new UserIdentity for the given user and provider.
     *
     * @param userId the id of the user to associate with the identity
     * @param provider the authentication provider
     * @param providerUserId the provider-specific user identifier
     * @return the persisted UserIdentity
     * @throws AuthException if an identity with the same provider and providerUserId already exists (DUPLICATE_IDENTITY)
     */
    @Transactional
    public UserIdentity create(Long userId, AuthProvider provider, String providerUserId) {
        if (userIdentityRepository.existsByProviderAndProviderUserId(provider, providerUserId)) {
            throw new AuthException(AuthErrorCode.DUPLICATE_IDENTITY);
        }
        return userIdentityRepository.save(UserIdentity.create(userId, provider, providerUserId));
    }

    /**
     * Deletes all user identity mappings associated with the specified user.
     *
     * @param userId the primary key of the user whose identities will be deleted
     */
    @Transactional
    public void deleteAllByUserId(Long userId) {
        userIdentityRepository.deleteAllByUserId(userId);
    }
}