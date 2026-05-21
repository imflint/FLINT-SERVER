package kr.flint.api.domain.content.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.content.domain.QContentGenre.*;
import static kr.flint.content.domain.QGenre.*;
import static kr.flint.ott.domain.QOttContent.*;
import static kr.flint.ott.domain.QOttProvider.*;
import static kr.flint.ott.domain.QOttUser.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.search.dto.response.GetSearchBookmarkContentRes;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<GetContentDetailRes> getContentDetailList(Long userId){

		// 1) 북마크된 contentId 10개 먼저 뽑기 (limit이 content 단위로 정확해짐)
		List<Long> topContentIds = jpaQueryFactory
			.select(contentBookmark.contentId)
			.from(contentBookmark)
			.where(contentBookmark.userId.eq(userId))
			.orderBy(contentBookmark.createdAt.desc())
			.limit(10)
			.fetch();

		if (topContentIds.isEmpty()) return List.of();

		// 2) 컨텐츠 기본 정보 (year/bookmark_count NULL 행 방어를 위해 coalesce)
		List<Tuple> contentRows = jpaQueryFactory
			.select(content.id, content.title, content.year.coalesce(0), content.poster, content.bookmarkCount.coalesce(0))
			.from(content)
			.where(content.id.in(topContentIds))
			.fetch();

		Map<Long, GetContentDetailRes> contentMap = new LinkedHashMap<>();
		for (Tuple row : contentRows) {
			Long id = row.get(content.id);
			if (id == null) continue;

			Integer year = row.get(content.year.coalesce(0));
			Integer bookmarkCount = row.get(content.bookmarkCount.coalesce(0));
			contentMap.put(id, new GetContentDetailRes(
				id,
				row.get(content.title),
				row.get(content.poster),
				year == null ? 0 : year,
				bookmarkCount == null ? 0 : bookmarkCount,
				new ArrayList<>()
			));
		}

		List<Tuple> ottRows = jpaQueryFactory
			.selectDistinct(
				ottContent.contentId,
				ottProvider.name,
				ottProvider.logoUrl
			)
			.from(ottContent)
			.join(ottContent.ottProvider, ottProvider)
			.leftJoin(ottUser).on(
				ottUser.userId.eq(userId),
				ottUser.ottProvider.eq(ottProvider)
			)
			.where(ottContent.contentId.in(topContentIds).and(ottUser.id.isNotNull()))
			.fetch();

		for (Tuple row : ottRows) {
			Long contentId = row.get(ottContent.contentId);
			if (contentId == null) continue;

			String ottName = row.get(ottProvider.name);
			String logoUrl = row.get(ottProvider.logoUrl);

			GetContentDetailRes dto = contentMap.get(contentId);
			if (dto == null) continue;

			if (ottName != null) {
				dto.getOttSimpleList().add(new GetContentDetailRes.GetOttSimpleRes(ottName, logoUrl));
			}
		}

		// 4) topContentIds 순서(북마크 최신순) 유지해서 반환
		List<GetContentDetailRes> result = new ArrayList<>();
		for (Long id : topContentIds) {
			GetContentDetailRes dto = contentMap.get(id);
			if (dto != null) result.add(dto);
		}
		return result;
	}

	public List<GetSearchBookmarkContentRes> getSearchBookmarkContent(Long userId, String keyword){
		List<Tuple> rows= jpaQueryFactory
			.select(
				content.id,
				content.title,
				content.author,
				content.poster,
				content.year,
				ottProvider.id,
				ottProvider.logoUrl,
				content.bookmarkCount
			)
			.from(content)
			.join(contentBookmark).on(
				contentBookmark.contentId.eq(content.id),
				contentBookmark.userId.eq(userId)
			)
			.leftJoin(ottContent).on(
				ottContent.contentId.eq(content.id)
			)
			.leftJoin(ottContent.ottProvider, ottProvider)
			.where(
				content.title.contains(keyword)
			)
			.orderBy(contentBookmark.createdAt.desc())
			.limit(10)
			.fetch();

		Map<Long, GetSearchBookmarkContentRes> contentMap = new LinkedHashMap<>();
		Map<Long, List<GetSearchBookmarkContentRes.GetOttSimpleRes>> ottMap = new LinkedHashMap<>();
		for (Tuple row : rows) {
			Long contentId = row.get(content.id);
			if (contentId == null) continue;
			String title = row.get(content.title);
			String author = row.get(content.author);
			String posterUrl = row.get(content.poster);
			int year = row.get(content.year);
			int bookmarkCount = row.get(content.bookmarkCount);

			Long ottId = row.get(ottProvider.id);
			String logoUrl = row.get(ottProvider.logoUrl);

			contentMap.computeIfAbsent(contentId, id ->
				new GetSearchBookmarkContentRes(
					id,
					title,
					author,
					posterUrl,
					year,
					new ArrayList<>(),
					bookmarkCount
				)
			);

			GetSearchBookmarkContentRes dto = contentMap.get(contentId);
			if (ottId != null) {
				dto.getOttSimpleList().add(new GetSearchBookmarkContentRes.GetOttSimpleRes(ottId, logoUrl));
			}
		}

		return new ArrayList<>(contentMap.values());
	}

	public List<GetContentSearchRes> searchContents(
		String keyword,
		List<String> genreNames,
		MediaType mediaType,
		int page,
		int size
	) {
		validatePageRequest(page, size);
		List<String> normalizedGenreNames = normalizeGenreNames(genreNames);
		if (normalizedGenreNames.isEmpty()) {
			return searchContentsWithoutGenre(keyword, mediaType, page, size);
		}

		return searchContentsWithGenres(keyword, normalizedGenreNames, mediaType, page, size);
	}

	private void validatePageRequest(int page, int size) {
		if (page < 1) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "page는 1 이상이어야 합니다.");
		}
		if (size < 1) {
			throw new GeneralException(ErrorCode.INVALID_INPUT, "size는 1 이상이어야 합니다.");
		}
	}

	private List<GetContentSearchRes> searchContentsWithoutGenre(
		String keyword,
		MediaType mediaType,
		int page,
		int size
	) {
		return jpaQueryFactory
			.select(
				content.id,
				content.title,
				content.author,
				content.poster,
				content.year
			)
			.from(content)
			.where(
				keywordCondition(keyword),
				mediaTypeCondition(mediaType)
			)
			.orderBy(content.bookmarkCount.desc(), content.id.desc())
			.offset((long) (page - 1) * size)
			.limit(size + 1L)
			.fetch()
			.stream()
			.map(this::toContentSearchRes)
			.toList();
	}

	// 요청한 모든 장르를 가진 콘텐츠를 인기순(bookmarkCount desc)으로 조회한다.
	private List<GetContentSearchRes> searchContentsWithGenres(
		String keyword,
		List<String> normalizedGenreNames,
		MediaType mediaType,
		int page,
		int size
	) {
		if (normalizedGenreNames.isEmpty()) {
			return List.of();
		}

		return jpaQueryFactory
			.select(
				content.id,
				content.title,
				content.author,
				content.poster,
				content.year
			)
			.from(content)
			.join(contentGenre).on(contentGenre.content.eq(content))
			.join(contentGenre.genre, genre)
			.where(
				keywordCondition(keyword),
				mediaTypeCondition(mediaType),
				genre.name.in(normalizedGenreNames)
			)
			.groupBy(
				content.id,
				content.title,
				content.author,
				content.poster,
				content.year,
				content.bookmarkCount
			)
			.having(genre.name.countDistinct().eq((long) normalizedGenreNames.size()))
			.orderBy(content.bookmarkCount.desc(), content.id.desc())
			.offset((long) (page - 1) * size)
			.limit(size + 1L)
			.fetch()
			.stream()
			.map(this::toContentSearchRes)
			.toList();
	}

	private GetContentSearchRes toContentSearchRes(Tuple row) {
		return GetContentSearchRes.of(
			row.get(content.id),
			row.get(content.title),
			row.get(content.author),
			row.get(content.poster),
			row.get(content.year) == null ? 0 : row.get(content.year)
		);
	}

	private BooleanExpression keywordCondition(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return content.title.containsIgnoreCase(keyword.trim());
	}

	private BooleanExpression mediaTypeCondition(MediaType mediaType) {
		return mediaType == null ? null : content.mediaType.eq(mediaType);
	}

	private List<String> normalizeGenreNames(List<String> genreNames) {
		if (genreNames == null || genreNames.isEmpty()) {
			return List.of();
		}

		return genreNames.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
	}
}
