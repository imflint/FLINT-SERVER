package kr.flint.exploration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.repository.UserExplorationProgressRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ExplorationProgressService {

	private final UserExplorationProgressRepository userExplorationProgressRepository;

	// 사용자의 진행 상태를 조회하고, 없으면 초기 상태로 생성한다.
	public UserExplorationProgress getOrCreate(Long userId) {
		return userExplorationProgressRepository.findByUserId(userId)
			.orElseGet(() -> userExplorationProgressRepository.save(UserExplorationProgress.create(userId)));
	}

	// 다음 세션으로 전진시킨다.
	public void advanceTo(UserExplorationProgress progress, Long nextSessionCursor) {
		progress.advance(nextSessionCursor);
		userExplorationProgressRepository.save(progress);
	}

	// 현재 세션에서 End에 도달했음을 기록한다.
	public void markCompleted(UserExplorationProgress progress) {
		progress.markCompleted();
		userExplorationProgressRepository.save(progress);
	}
}
