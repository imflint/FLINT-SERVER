package kr.flint.auth.repository;

import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    void deleteAllByUserId(Long userId);
}
