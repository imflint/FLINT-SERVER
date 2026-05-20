package kr.flint.moderation.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_moderation_histories")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserModerationHistory extends BaseTime {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserModerationAction action;

    private LocalDateTime actionExpiresAt;

    @Column(length = 500)
    private String adminMemo;

    public static UserModerationHistory create(
        Long userId,
        Long adminUserId,
        UserModerationAction action,
        LocalDateTime actionExpiresAt,
        String adminMemo
    ) {
        return UserModerationHistory.builder()
            .userId(userId)
            .adminUserId(adminUserId)
            .action(action)
            .actionExpiresAt(actionExpiresAt)
            .adminMemo(adminMemo)
            .build();
    }
}
