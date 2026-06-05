package kr.flint.bookmark.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.repository.ContentBookmarkRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkQueryService {
	private final CollectionBookmarkRepository collectionBookmarkRepository;
	private final ContentBookmarkRepository contentBookmarkRepository;

	public int getBookmarkCount(final Long collectionId) {
		return collectionBookmarkRepository.countByCollectionId(collectionId);
	}

	public boolean isContentBookmarked(final Long userId, final Long contentId) {
		return contentBookmarkRepository.existsByContentIdAndUserId(contentId, userId);
	}

	public int getContentBookmarkCount(final Long userId) {
		return contentBookmarkRepository.countByUserId(userId);
	}

	public List<Long> getBookmarkUserId(final Long collectionId) {
		return collectionBookmarkRepository.findUserIdsByCollectionId(collectionId);
	}

	public List<Long> getBookmarkedCollectionIds(final Long userId) {
		return collectionBookmarkRepository.findCollectionIdsByUserId(userId);
	}

	public Set<Long> getBookmarkedCollectionIdSet(final Long userId) {
		if (userId == null) {
			return Collections.emptySet();
		}
		return new HashSet<>(collectionBookmarkRepository.findCollectionIdsByUserId(userId));
	}
}
