package kr.flint.moderation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.moderation.domain.UserModerationHistory;

public interface UserModerationHistoryRepository extends JpaRepository<UserModerationHistory, Long> {

    List<UserModerationHistory> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
