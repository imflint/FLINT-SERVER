package kr.flint.bookmark.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	// 북마크한 컬렉션 ID Set 조회 (비로그인 시 빈 Set 반환)
	public Set<Long> getBookmarkedCollectionIdSet(final Long userId) {
		if (userId == null) {
			return Collections.emptySet();
		}
		return new HashSet<>(collectionBookmarkRepository.findCollectionIdsByUserId(userId));
	}

	@Transactional
	public void createContentBookmarks(final Long userId, final List<Long> contentIds) {
		if (CollectionUtils.isEmpty(contentIds)) {
			return;
		}

		List<ContentBookmark> bookmarks = contentIds.stream()
			.map(contentId -> ContentBookmark.create(userId, contentId))
			.toList();

		contentBookmarkRepository.saveAll(bookmarks);
	}
}
