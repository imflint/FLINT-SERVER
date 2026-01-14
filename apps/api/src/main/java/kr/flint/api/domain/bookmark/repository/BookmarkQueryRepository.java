package kr.flint.api.domain.bookmark.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.content.domain.QContentGenre.*;
import static kr.flint.content.domain.QGenre.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BookmarkQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<TasteWorkMetaDto> getBookmarkWorkMeta(Long userId) {

		// 1) 북마크된 콘텐츠 기본 메타 조회
		List<Tuple> baseResults = jpaQueryFactory
			.select(
				content.id,
				content.title,
				content.description
			)
			.from(content)
			.join(contentBookmark).on(
				contentBookmark.userId.eq(userId),
				contentBookmark.contentId.eq(content.id)
			)
			.fetch();

		// 북마크가 없으면 바로 반환
		if (baseResults.isEmpty()) {
			return List.of();
		}

		List<Long> contentIds = baseResults.stream()
			.map(t -> t.get(content.id))
			.toList();

		// 2) 장르 조회 (contentId -> genres)
		Map<Long, List<String>> genreMap = jpaQueryFactory
			.select(
				contentGenre.content.id,
				genre.name
			)
			.from(contentGenre)
			.join(genre).on(contentGenre.genre.eq(genre))
			.where(contentGenre.content.id.in(contentIds))
			.fetch()
			.stream()
			.collect(Collectors.groupingBy(
				t -> t.get(contentGenre.content.id),
				Collectors.mapping(t -> t.get(genre.name), Collectors.toList())
			));


		// 4) DTO 조립
		return baseResults.stream()
			.map(t -> {
				Long contentId = t.get(content.id);
				return new TasteWorkMetaDto(
					contentId,
					t.get(content.title),
					genreMap.getOrDefault(contentId, List.of()),
					t.get(content.description)
				);
			})
			.toList();
	}

}
