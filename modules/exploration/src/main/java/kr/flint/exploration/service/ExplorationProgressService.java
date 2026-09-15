package kr.flint.exploration.service;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.domain.UserExplorationSessionItem;
import kr.flint.exploration.repository.UserExplorationProgressRepository;
import kr.flint.exploration.repository.UserExplorationSessionItemRepository;
import lombok.RequiredArgsConstructor;
import io.hypersistence.tsid.TSID;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

@Service
@RequiredArgsConstructor
@Transactional
public class ExplorationProgressService {

	private final UserExplorationProgressRepository userExplorationProgressRepository;
	private final UserExplorationSessionItemRepository userExplorationSessionItemRepository;

	// 사용자의 진행 상태를 조회하고, 없으면 초기 상태로 생성한다.
	public UserExplorationProgress getOrCreate(Long userId) {
		return userExplorationProgressRepository.findByUserId(userId)
			.orElseGet(() -> userExplorationProgressRepository.save(UserExplorationProgress.create(userId)));
	}

	public UserExplorationProgress getOrCreateForUpdate(Long userId) {
		return userExplorationProgressRepository.findByUserIdForUpdate(userId)
			.orElseGet(() -> {
				userExplorationProgressRepository.insertInitialProgress(
					TSID.Factory.getTsid().toLong(),
					userId,
					LocalDateTime.now()
				);
				return userExplorationProgressRepository.findByUserIdForUpdate(userId)
					.orElseThrow(() -> new GeneralException(ErrorCode.INTERNAL_SERVER_ERROR));
			});
	}

	public List<UserExplorationSessionItem> getSessionItems(UserExplorationProgress progress) {
		return userExplorationSessionItemRepository.findAllByUserIdAndSessionVersionOrderByPosition(
			progress.getUserId(),
			progress.getSessionVersion()
		);
	}

	public List<UserExplorationSessionItem> saveSessionItems(List<UserExplorationSessionItem> items) {
		return userExplorationSessionItemRepository.saveAll(items);
	}

	// 다음 세션으로 전진시킨다.
	public void advanceTo(UserExplorationProgress progress, Long nextSessionCursor) {
		progress.advance(nextSessionCursor);
		userExplorationProgressRepository.save(progress);
	}

	// 현재 세션에서 노출 가능한 작품을 모두 확인했음을 기록한다.
	public void markCompleted(UserExplorationProgress progress) {
		progress.markCompleted();
		userExplorationProgressRepository.save(progress);
	}

	public void recordViewedPosition(UserExplorationProgress progress, int position) {
		progress.recordViewedPosition(position);
		userExplorationProgressRepository.save(progress);
	}

	public void deleteByUser(Long userId) {
		userExplorationSessionItemRepository.deleteAllByUserId(userId);
		userExplorationProgressRepository.deleteAllByUserId(userId);
	}
}
