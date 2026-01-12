package kr.flint.bookmark.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.domain.CollectionBookmark;
import kr.flint.bookmark.domain.ContentBookmark;
import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.repository.ContentBookmarkRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {
	private final CollectionBookmarkRepository collectionBookmarkRepository;
	private final ContentBookmarkRepository contentBookmarkRepository;

	@Transactional
	public boolean toggleContent(final Long userId, final Long contentId) {
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

	@Transactional
	public boolean toggleCollection(final Long userId, final Long collectionId) {
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

	public int getBookmarkCount(final Long collectionId) {
		return collectionBookmarkRepository.countByCollectionId(collectionId);
	}

	public List<Long> getBookmarkUserId(final Long collectionId){
		return collectionBookmarkRepository.findUserIdsByCollectionId(collectionId);
	}

	public List<Long> getBookmarkedCollectionIds(final Long userId) {
		return collectionBookmarkRepository.findCollectionIdsByUserId(userId);
	}
}
