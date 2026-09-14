package kr.flint.batch.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class TmdbChangeWindowService {

	private static final int MAX_WINDOW_DAYS = 14;

	public List<DateWindow> split(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("TMDB change date range is invalid");
		}
		List<DateWindow> result = new ArrayList<>();
		LocalDate cursor = startDate;
		while (cursor.isBefore(endDate)) {
			LocalDate windowEnd = cursor.plusDays(MAX_WINDOW_DAYS);
			if (windowEnd.isAfter(endDate)) {
				windowEnd = endDate;
			}
			result.add(new DateWindow(cursor, windowEnd));
			cursor = windowEnd;
		}
		return List.copyOf(result);
	}

	public record DateWindow(LocalDate startDate, LocalDate endDate) {
	}
}
