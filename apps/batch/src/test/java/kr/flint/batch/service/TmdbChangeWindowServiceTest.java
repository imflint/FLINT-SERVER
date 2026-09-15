package kr.flint.batch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TmdbChangeWindowServiceTest {

	private final TmdbChangeWindowService service = new TmdbChangeWindowService();

	@Test
	void splitsLongGapIntoAtMostFourteenDayWindowsWithoutGaps() {
		var windows = service.split(
			LocalDate.of(2026, 8, 1),
			LocalDate.of(2026, 9, 13)
		);

		assertThat(windows).hasSize(4);
		assertThat(windows.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
		assertThat(windows.getLast().endDate()).isEqualTo(LocalDate.of(2026, 9, 13));
		for (int index = 1; index < windows.size(); index++) {
			assertThat(windows.get(index).startDate()).isEqualTo(windows.get(index - 1).endDate());
		}
		assertThat(windows).allSatisfy(window ->
			assertThat(java.time.temporal.ChronoUnit.DAYS.between(window.startDate(), window.endDate()))
				.isLessThanOrEqualTo(14)
		);
	}
}
