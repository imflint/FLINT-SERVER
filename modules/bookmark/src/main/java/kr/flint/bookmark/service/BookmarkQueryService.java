package kr.flint.bookmark.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkQueryService {
	private final CollectionBookmarkRepository collectionBookmarkRepository;

	public int getBookmarkCount(final Long collectionId) {
		return collectionBookmarkRepository.countByCollectionId(collectionId);
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
