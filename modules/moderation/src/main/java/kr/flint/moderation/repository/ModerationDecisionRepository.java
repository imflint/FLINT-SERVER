package kr.flint.moderation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.flint.moderation.domain.ModerationDecision;
import kr.flint.moderation.domain.ModerationSourceType;

public interface ModerationDecisionRepository extends JpaRepository<ModerationDecision, Long> {
    Optional<ModerationDecision> findFirstBySourceTypeAndSourceIdOrderByCreatedAtDesc(
        ModerationSourceType sourceType,
        Long sourceId
    );
}
