package kr.flint.api.domain.collection.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.flint.collection.event.CollectionReportedEvent;
import kr.flint.infra.discord.service.DiscordReportNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 신고 트랜잭션 commit 이후에만 Discord 알림 전송 — 알림 실패가 신고 자체를 무효화하지 않도록 분리.
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionReportEventListener {

	private final DiscordReportNotifier discordReportNotifier;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCollectionReported(CollectionReportedEvent event) {
		log.info("notify discord on collection report: reportId={}", event.reportId());
		discordReportNotifier.notifyCollectionReport(
			event.reportId(),
			event.reporterId(),
			event.collectionId(),
			event.reasonLabels(),
			event.otherDetail()
		);
	}
}
