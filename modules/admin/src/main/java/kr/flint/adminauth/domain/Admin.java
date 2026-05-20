package kr.flint.adminauth.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Admin extends BaseTime {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    private LocalDateTime passwordChangedAt;

    public static Admin create(
        String username,
        String passwordHash,
        LocalDateTime now
    ) {
        return Admin.builder()
            .username(username)
            .passwordHash(passwordHash)
            .passwordChangedAt(now)
            .build();
    }

    public void changeUsername(String username) {
        this.username = username;
    }

    public void changePassword(String passwordHash, LocalDateTime now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
    }

    // todo: 컬렉션 수정 api
}
