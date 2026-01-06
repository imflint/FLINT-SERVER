package kr.flint.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.flint.shared.domain.BaseTime;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTime {

    @Column(nullable = false)
    private String realName;

    @Column(nullable = false, length = 10, unique = true)
    private String nickname;

    private String profileImage;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime deletedAt;

    // TODO: 프로필 이미지 어떻게 할지 고민
    public static User createFling(String realName, String nickname) {
        return create(realName, nickname, null, UserRole.FLING);
    }

    public static User createFliner(String realName, String nickname) {
        return create(realName, nickname, null, UserRole.FLINER);
    }

    private static User create(String realName, String nickname, String profileImage, UserRole userRole) {
        validateRealName(realName);
        validateNickname(nickname);
        return User.builder()
                .realName(realName)
                .profileImage(profileImage)
                .nickname(nickname)
                .userRole(userRole)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void updateNickname(String nickname) {
        validateNickname(nickname);
        this.nickname = nickname;
    }

    public void updateProfile(String profileImage) {
        this.profileImage = profileImage;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    private static void validateRealName(String realName) {
        if (!StringUtils.hasText(realName)) {
            throw new UserException(UserErrorCode.INVALID_REAL_NAME);
        }
    }

    private static void validateNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new UserException(UserErrorCode.INVALID_NICKNAME);
        }
    }
}
