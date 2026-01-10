package kr.flint.user.service;

import java.util.List;

import kr.flint.user.domain.User;
import kr.flint.user.dto.response.UserSimpleRes;
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

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    public User getByNickname(String nickname) {
        return userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

	public List<UserSimpleRes> getUserInfoList(List<Long> userIdList) {
		if (userIdList == null || userIdList.isEmpty()) {
			return null;
		}
		return userRepository.findByIds(userIdList);
	}
}
