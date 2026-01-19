package kr.flint.bookmark.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.domain.CollectionBookmark;
import kr.flint.bookmark.domain.ContentBookmark;
import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.repository.ContentBookmarkRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BookmarkCommandService {
	private final CollectionBookmarkRepository collectionBookmarkRepository;
	private final ContentBookmarkRepository contentBookmarkRepository;

	public boolean toggleContent(Long userId, Long contentId) {
		return contentBookmarkRepository.findByContentIdAndUserId(contentId, userId)
			.map(
				contentBookmark -> {
					contentBookmarkRepository.delete(contentBookmark);
					return false;
				})
			.orElseGet(() -> {
					contentBookmarkRepository.save(ContentBookmark.create(userId, contentId));
					return true;
				}
			);
	}

	public boolean toggleCollection(Long userId, Long collectionId) {
		return collectionBookmarkRepository.findByCollectionIdAndUserId(collectionId, userId)
			.map(
				collectionBookmark -> {
					collectionBookmarkRepository.delete(collectionBookmark);
					return false;
				}
			)
			.orElseGet(()-> {
					collectionBookmarkRepository.save(CollectionBookmark.create(userId, collectionId));
					return true;
				}
			);
	}

	public void createContentBookmarks(Long userId, List<Long> contentIds) {
		if (CollectionUtils.isEmpty(contentIds)) {
			return;
		}

		List<ContentBookmark> bookmarks = contentIds.stream()
			.map(contentId -> ContentBookmark.create(userId, contentId))
			.toList();

		contentBookmarkRepository.saveAll(bookmarks);
	}
}
