package kr.flint.auth.repository;

import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /**
 * Finds a UserIdentity matching the given authentication provider and provider-specific user ID.
 *
 * @param provider the authentication provider to match
 * @param providerUserId the user ID assigned by the provider
 * @return an Optional containing the matching UserIdentity, or Optional.empty() if no match is found
 */
Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
 * Determine whether a UserIdentity exists for the given authentication provider and provider-specific user ID.
 *
 * @param provider the authentication provider
 * @param providerUserId the user identifier assigned by the provider
 * @return {@code true} if a matching UserIdentity exists, {@code false} otherwise
 */
boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
 * Delete all UserIdentity entities associated with the given user id.
 *
 * @param userId the id of the user whose identities should be deleted
 */
void deleteAllByUserId(Long userId);
}