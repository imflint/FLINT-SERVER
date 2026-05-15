package kr.flint.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.flint.shared.domain.BaseTime;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTime {

//    @Column(nullable = false)
//    private String realName;

    @Column(nullable = false, length = NicknamePolicy.MAX_LENGTH, unique = true)
    private String nickname;

    private String profileImage;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime deletedAt;

    // 마지막 GPT 키워드 재계산 이후 추가된 컨텐츠 북마크 수 — 임계값 도달 시 재계산 트리거에 사용
    @Column(nullable = false)
    @ColumnDefault("0")
    private int bookmarksSinceKeywordCalc;

    @ColumnDefault("0")
    private Integer warningCount;

    private LocalDateTime uploadRestrictedAt;

    private LocalDateTime uploadRestrictedUntil;

    private LocalDateTime suspendedAt;

    private LocalDateTime suspendedUntil;

    public static User createFling(String nickname, String profileImage) {
        return create(nickname, profileImage, UserRole.FLING);
    }

    public static User createFling(String nickname) {
        return createFling(nickname, null);
    }


    private static User create(String nickname, String profileImage, UserRole userRole) {
        validateNickname(nickname);
        return User.builder()
//                .realName(realName)
                .profileImage(profileImage)
                .nickname(nickname)
                .userRole(userRole)
                .status(UserStatus.ACTIVE)
                .warningCount(0)
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

    public void warn() {
        this.warningCount = warningCount == null ? 1 : warningCount + 1;
    }

    public void restrictUpload(LocalDateTime restrictedAt, LocalDateTime restrictedUntil) {
        this.uploadRestrictedAt = restrictedAt;
        this.uploadRestrictedUntil = restrictedUntil;
    }

    public void suspend(LocalDateTime suspendedAt, LocalDateTime suspendedUntil) {
        this.suspendedAt = suspendedAt;
        this.suspendedUntil = suspendedUntil;
    }

    public boolean canUpload(LocalDateTime now) {
        return canUseService(now) && !hasActiveUploadRestriction(now);
    }

    public boolean canUseService(LocalDateTime now) {
        return status == UserStatus.ACTIVE && !hasActiveSuspension(now);
    }

    public boolean hasActiveSuspension(LocalDateTime now) {
        return isActiveSanctionWindow(suspendedAt, suspendedUntil, now);
    }

    private boolean hasActiveUploadRestriction(LocalDateTime now) {
        return isActiveSanctionWindow(uploadRestrictedAt, uploadRestrictedUntil, now);
    }

    private boolean isActiveSanctionWindow(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        return startsAt != null
            && !startsAt.isAfter(now)
            && (endsAt == null || endsAt.isAfter(now));
    }

    public void incrementBookmarksSinceKeywordCalc() {
        this.bookmarksSinceKeywordCalc++;
    }

    public void resetBookmarksSinceKeywordCalc() {
        this.bookmarksSinceKeywordCalc = 0;
    }

    private static void validateNickname(String nickname) {
        NicknamePolicy.validate(nickname);
    }
}
