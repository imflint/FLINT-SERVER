package kr.flint.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.repository.UserExplorationProgressRepository;
import kr.flint.exploration.repository.UserExplorationSessionItemRepository;

@ExtendWith(MockitoExtension.class)
class ExplorationProgressServiceTest {

	@Mock private UserExplorationProgressRepository progressRepository;
	@Mock private UserExplorationSessionItemRepository itemRepository;

	@Test
	@DisplayName("초기 진행 행은 INSERT IGNORE 후 비관적 잠금으로 다시 조회")
	void createsInitialProgressThenLocksIt() {
		ExplorationProgressService service = new ExplorationProgressService(progressRepository, itemRepository);
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		when(progressRepository.findByUserIdForUpdate(1L))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(progress));

		UserExplorationProgress result = service.getOrCreateForUpdate(1L);

		assertThat(result).isSameAs(progress);
		verify(progressRepository).insertInitialProgress(anyLong(), org.mockito.ArgumentMatchers.eq(1L), any(LocalDateTime.class));
		verify(progressRepository, times(2)).findByUserIdForUpdate(1L);
	}

	@Test
	@DisplayName("회원 탈퇴 시 세션 항목을 먼저 지우고 진행 행을 삭제")
	void deletesUserExplorationData() {
		ExplorationProgressService service = new ExplorationProgressService(progressRepository, itemRepository);

		service.deleteByUser(1L);

		org.mockito.InOrder order = org.mockito.Mockito.inOrder(itemRepository, progressRepository);
		order.verify(itemRepository).deleteAllByUserId(1L);
		order.verify(progressRepository).deleteAllByUserId(1L);
	}
}
