package kr.flint.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kr.flint.shared.domain.BaseTime;
import kr.flint.shared.exception.GeneralException;
import kr.flint.user.exception.UserErrorCode;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.usertype.UserType;

import java.time.LocalDateTime;
import java.util.function.Predicate;

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

    @Column(nullable = false)
    private String nickNameNorm;

    private String profileImage;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime deletedAt;
}
