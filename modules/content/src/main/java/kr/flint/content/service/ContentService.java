package kr.flint.content.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.content.domain.Content;
import kr.flint.content.domain.ContentGenre;
import kr.flint.content.domain.Genre;
import kr.flint.content.dto.ContentWithGenres;
import kr.flint.content.exception.ContentErrorCode;
import kr.flint.content.exception.ContentException;
import kr.flint.content.repository.ContentGenreRepository;
import kr.flint.content.repository.ContentRepository;
import kr.flint.content.repository.GenreRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContentService {
	private final ContentRepository contentRepository;
	private final ContentGenreRepository contentGenreRepository;
	private final GenreRepository genreRepository;

	public Content getContentById(final Long contentId) {
		return contentRepository.findById(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND));
	}

	@Transactional
	public void increaseBookmarkCount(final Long contentId) {
		Content content = getContentById(contentId);
		content.increaseBookmarkCount();
	}

	@Transactional
	public void decreaseBookmarkCount(final Long contentId) {
		Content content = getContentById(contentId);
		content.decreaseBookmarkCount();
	}

	@Transactional
	public Content tmdbToDb(final Content content, List<Genre> genreList) {
		Content savedContent = contentRepository.save(content);
		List<Genre> savedGenreList = genreList != null ? genreRepository.saveAll(genreList) : List.of();
		List<ContentGenre> contentGenreList = savedGenreList.stream()
			.map(genre -> ContentGenre.create(savedContent, genre))
			.toList();

		contentGenreRepository.saveAll(contentGenreList);

		return savedContent;
	}
	public boolean checkGenre(final String genre) {
		return genreRepository.existsByName(genre);
	}

	public Content getContentByTmdbId(final Long tmdbId) {
		return contentRepository.findContentByTmdbId(tmdbId)
			.orElse(null);
	}

	public List<Content> getContentByTitle(final String title) {
		return contentRepository.findAllByTitleContaining(title);
	}

	public List<Content> getAllContent() {
		return contentRepository.findAll();
	}

	// 콘텐츠 ID 목록으로 콘텐츠 + 장르 정보 조회
	public List<ContentWithGenres> getContentsWithGenres(List<Long> contentIds) {
		if (contentIds == null || contentIds.isEmpty()) {
			return List.of();
		}

		List<Content> contents = contentRepository.findAllById(contentIds);
		List<ContentGenre> contentGenres = contentGenreRepository.findAllByContentIdsWithGenre(contentIds);

		// contentId -> genre names 매핑
		Map<Long, List<String>> genreMap = contentGenres.stream()
			.collect(Collectors.groupingBy(
				cg -> cg.getContent().getId(),
				Collectors.mapping(cg -> cg.getGenre().getName(), Collectors.toList())
			));

		return contents.stream()
			.map(content -> ContentWithGenres.of(
				content,
				genreMap.getOrDefault(content.getId(), List.of())
			))
			.toList();
	}
}
