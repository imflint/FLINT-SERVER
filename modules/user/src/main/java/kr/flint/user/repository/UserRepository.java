package kr.flint.user.repository;

import kr.flint.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
 * Finds a user by nickname.
 *
 * @param nickname the nickname to match
 * @return an Optional containing the matching User if present, or Optional.empty() if no match is found
 */
Optional<User> findByNickname(String nickname);

    /**
 * Checks whether a user with the given nickname exists in the data store.
 *
 * @param nickname the user nickname to check for existence
 * @return `true` if a user with the nickname exists, `false` otherwise
 */
boolean existsByNickname(String nickname);
}