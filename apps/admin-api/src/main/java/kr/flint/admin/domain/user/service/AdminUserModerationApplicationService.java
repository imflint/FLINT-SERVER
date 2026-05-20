package kr.flint.admin.domain.user.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminUserModerationApplicationService {

    private final UserService userService;

    public void apply(Long userId, UserModerationAction action, LocalDateTime expiresAt) {
        switch (action) {
            case WARN -> userService.warn(userId);
            case RESTRICT_UPLOAD -> userService.restrictUpload(userId, expiresAt);
            case SUSPEND -> userService.suspend(userId, expiresAt);
            case KEEP -> {
            }
        }
    }
}
