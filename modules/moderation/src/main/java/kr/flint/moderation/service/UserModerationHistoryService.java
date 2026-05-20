package kr.flint.moderation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.domain.UserModerationHistory;
import kr.flint.moderation.repository.UserModerationHistoryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserModerationHistoryService {

    private final UserModerationHistoryRepository userModerationHistoryRepository;

    @Transactional
    public UserModerationHistory record(
        Long userId,
        Long adminUserId,
        UserModerationAction action,
        LocalDateTime actionExpiresAt,
        String adminMemo
    ) {
        return userModerationHistoryRepository.save(UserModerationHistory.create(
            userId,
            adminUserId,
            action,
            actionExpiresAt,
            adminMemo
        ));
    }

    public List<UserModerationHistory> getRecentHistories(Long userId) {
        return userModerationHistoryRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }
}
