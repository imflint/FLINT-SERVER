package kr.flint.user.service;

import kr.flint.user.domain.User;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
import kr.flint.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * Fetches the user for the given ID.
     *
     * @param userId the ID of the user to retrieve
     * @return the User with the specified ID
     * @throws UserException if no user exists with the given ID (UserErrorCode.USER_NOT_FOUND)
     */
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * Retrieves the User with the given nickname.
     *
     * @param nickname the user's nickname
     * @return the matching User
     * @throws UserException if no user with the given nickname exists (UserErrorCode.USER_NOT_FOUND)
     */
    public User getByNickname(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * Checks whether a user with the given nickname exists.
     *
     * @param nickname the nickname to check for existence
     * @return `true` if a user with the given nickname exists, `false` otherwise
     */
    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}