package kr.flint.api.domain.auth.event;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.flint.content.dto.ContentWithGenres;
import kr.flint.content.service.ContentService;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.api.domain.user.service.TasteAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TasteAnalysisEventHandler {

    private final ContentService contentService;
    private final TasteAnalysisService tasteAnalysisService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserSignedUp(UserSignedUpEvent event) {
        log.debug("취향 분석 시작. userId={}, contentCount={}", event.userId(), event.contentIds().size());

        try {
            List<ContentWithGenres> contents = contentService.getContentsWithGenres(event.contentIds());

            if (contents.isEmpty()) {
                log.warn("분석할 콘텐츠가 없음 - userId: {}", event.userId());
                return;
            }

            List<TasteWorkMetaDto> workMetaList = contents.stream()
                .map(c -> new TasteWorkMetaDto(c.contentId(), c.title(), c.genreList(), c.overview()))
                .toList();
			tasteAnalysisService.analyze(event.userId(), workMetaList);

			log.debug("취향 분석 완료. userId={}", event.userId());
        } catch (Exception e) {
            log.error("취향 분석 실패 - userId: {}", event.userId(), e);
        }
    }
}
