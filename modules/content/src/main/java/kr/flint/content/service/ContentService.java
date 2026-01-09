package kr.flint.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.content.domain.Content;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContentService {
	private final ContentRepository contentRepository;

	public Content getContentById(final Long contentId) {
		return contentRepository.findById(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND));
	}

	@Transactional
	public void increaseBookmarkCount(final Long contentId) {
		Content content = getContentById(contentId);
		content.increaseBookmarkCount();
	}

	@Transactional void decreaseBookmarkCount(final Long contentId) {
		Content content = getContentById(contentId);
		content.decreaseBookmarkCount();
	}
}
