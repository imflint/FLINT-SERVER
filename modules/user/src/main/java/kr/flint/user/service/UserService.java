package kr.flint.user.service;

import java.util.List;

import org.springframework.util.CollectionUtils;

import kr.flint.user.domain.User;
import kr.flint.user.dto.response.UserAuthInfo;
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

    // 인증용 사용자 정보 조회
    public UserAuthInfo getAuthInfo(Long userId) {
        User user = getById(userId);
        return UserAuthInfo.of(user.getId(), user.getUserRole().name());
    }

    @Transactional
    public UserAuthInfo create(String nickname) {
        if (existsByNickname(nickname)) {
            throw new UserException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.createFling(nickname);
        User saved = userRepository.save(user);
        return UserAuthInfo.of(saved.getId(), saved.getUserRole().name());
    }
	public List<UserSimpleRes> getUserInfoList(List<Long> userIdList) {
		if (CollectionUtils.isEmpty(userIdList)) {
			return null;
		}
		return userRepository.findByIds(userIdList);
	}
}
