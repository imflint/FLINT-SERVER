package kr.flint.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.util.CollectionUtils;

import kr.flint.user.domain.NicknamePolicy;
import kr.flint.user.domain.User;
import kr.flint.user.domain.UserStatus;
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
            validateCanUseService(user);
            return UserAuthInfo.of(user.getId(), user.getNickname(), user.getUserRole().name());
        }

    @Transactional
    public UserAuthInfo create(String nickname) {
        return create(nickname, null);
    }

    @Transactional
    public UserAuthInfo create(String nickname, String profileImage) {
        NicknamePolicy.validate(nickname);
        if (existsByNickname(nickname)) {
            throw new UserException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.createFling(nickname, profileImage);
        User saved = userRepository.save(user);
        return UserAuthInfo.of(saved.getId(), saved.getNickname(), saved.getUserRole().name());
    }
    public List<UserSimpleRes> getUserInfoList(List<Long> userIdList) {
        if (CollectionUtils.isEmpty(userIdList)) {
            return null;
        }
        return userRepository.findByIds(userIdList);
    }

    @Transactional
    public void updateProfileImage(Long userId, String profileImage) {
        User user = getById(userId);
        user.updateProfile(profileImage);
    }

    @Transactional
        public void updateNickname(Long userId, String nickname) {
        NicknamePolicy.validate(nickname);
        if (existsByNickname(nickname)) {
            throw new UserException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        User user = getById(userId);
        user.updateNickname(nickname);
    }

        @Transactional
        public void deleteUser(Long userId){
            userRepository.deleteById(userId);
        }

        @Transactional
        public void warn(Long userId) {
            getById(userId).warn();
        }

        @Transactional
        public void restrictUpload(Long userId, LocalDateTime expiresAt) {
            getById(userId).restrictUpload(LocalDateTime.now(), expiresAt);
        }

        @Transactional
        public void suspend(Long userId, LocalDateTime expiresAt) {
            getById(userId).suspend(LocalDateTime.now(), expiresAt);
        }

        public void validateCanUpload(Long userId) {
            User user = getById(userId);
            LocalDateTime now = LocalDateTime.now();
            if (!user.canUseService(now)) {
                throw new UserException(UserErrorCode.USER_SUSPENDED);
            }
            if (!user.canUpload(now)) {
                throw new UserException(UserErrorCode.USER_UPLOAD_RESTRICTED);
            }
        }

        public boolean canUseService(Long userId) {
            return userRepository.findById(userId)
                .map(user -> user.canUseService(LocalDateTime.now()))
                .orElse(false);
        }

        public long countActiveUsers() {
            return userRepository.countActiveUsers(LocalDateTime.now());
        }

    // 컨텐츠 북마크 ON 때마다 호출 — 카운터만 +1, 임계값 판단/리셋은 호출처가 담당
    @Transactional
        public void incrementContentBookmarkCounter(Long userId) {
            User user = getById(userId);
            user.incrementBookmarksSinceKeywordCalc();
        }

        private void validateCanUseService(User user) {
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new UserException(UserErrorCode.ALREADY_WITHDRAWN);
            }
            if (user.hasActiveSuspension(LocalDateTime.now())) {
                throw new UserException(UserErrorCode.USER_SUSPENDED);
            }
        }
    }
