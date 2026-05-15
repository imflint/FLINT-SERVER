package kr.flint.moderation.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.moderation.domain.CollectionModerationAction;
import kr.flint.moderation.domain.ModerationDecision;
import kr.flint.moderation.domain.ModerationSourceType;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.repository.ModerationDecisionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModerationDecisionService {

    private final ModerationDecisionRepository moderationDecisionRepository;

    @Transactional
    public ModerationDecision recordCollectionReportDecision(
        Long reportId,
        Long collectionId,
        Long userId,
        Long adminUserId,
        CollectionModerationAction collectionAction,
        UserModerationAction userAction,
        LocalDateTime userActionExpiresAt,
        String adminMemo
    ) {
        return moderationDecisionRepository.save(ModerationDecision.createCollectionReportDecision(
            reportId,
            collectionId,
            userId,
            adminUserId,
            collectionAction,
            userAction,
            userActionExpiresAt,
            adminMemo
        ));
    }

    public Optional<ModerationDecision> findCollectionReportDecision(Long reportId) {
        return moderationDecisionRepository.findFirstBySourceTypeAndSourceIdOrderByCreatedAtDesc(
            ModerationSourceType.COLLECTION_REPORT,
            reportId
        );
    }
}
