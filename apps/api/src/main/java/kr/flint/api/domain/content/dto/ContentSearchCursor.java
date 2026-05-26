package kr.flint.api.domain.content.dto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.util.StringUtils;

import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

public record ContentSearchCursor(
	int bookmarkCount,
	Long contentId
) {
	private static final String DELIMITER = ":";

	public ContentSearchCursor {
		if (bookmarkCount < 0 || contentId == null || contentId <= 0) {
			throw invalidCursor();
		}
	}

	public static ContentSearchCursor of(int bookmarkCount, Long contentId) {
		return new ContentSearchCursor(bookmarkCount, contentId);
	}

	public static ContentSearchCursor decodeNullable(String cursor) {
		if (!StringUtils.hasText(cursor)) {
			return null;
		}
		return decode(cursor);
	}

	public static ContentSearchCursor decode(String cursor) {
		try {
			if (!StringUtils.hasText(cursor)) {
				throw invalidCursor();
			}
			String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = payload.split(DELIMITER, -1);
			if (parts.length != 2) {
				throw invalidCursor();
			}
			return of(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
		} catch (IllegalArgumentException exception) {
			throw invalidCursor();
		}
	}

	public String encode() {
		String payload = bookmarkCount + DELIMITER + contentId;
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static GeneralException invalidCursor() {
		return new GeneralException(ErrorCode.INVALID_INPUT, "cursor 형식이 올바르지 않습니다.");
	}
}
