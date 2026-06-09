package kr.flint.api.domain.content.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.content.domain.QContentGenre.*;
import static kr.flint.content.domain.QGenre.*;
import static kr.flint.ott.domain.QOttContent.*;
import static kr.flint.ott.domain.QOttProvider.*;
import static kr.flint.ott.domain.QOttUser.*;
import static kr.flint.shared.util.QueryDslUtil.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.search.dto.response.GetSearchBookmarkContentRes;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepository {
	private static final Pattern FULLTEXT_BOOLEAN_OPERATOR_PATTERN = Pattern.compile("[+\\-<>()~*\"@]");

	private final JPAQueryFactory jpaQueryFactory;

	public record ContentSearchRow(
		Long id,
		String title,
		String author,
		String posterUrl,
		int year,
		int bookmarkCount
	) {
		public GetContentSearchRes toResponse() {
			return GetContentSearchRes.of(id, title, author, posterUrl, year);
		}
	}

	public List<GetContentDetailRes> getContentDetailList(Long userId){
		// limit 0 → 북마크한 작품 전체 반환
		return getBookmarkedContentRows(userId, null, 0).stream()
			.map(BookmarkedContentRow::toResponse)
			.toList();
	}

	// limit <= 0 이면 제한 없이 전체 조회
	public List<BookmarkedContentRow> getBookmarkedContentRows(Long userId, Long cursor, int limit) {
		var bookmarkQuery = jpaQueryFactory
			.select(contentBookmark.id, contentBookmark.contentId)
			.from(contentBookmark)
			.where(
				contentBookmark.userId.eq(userId),
				cursor != null ? contentBookmark.id.lt(cursor) : null
			)
			.orderBy(contentBookmark.id.desc());

		if (limit > 0) {
			bookmarkQuery.limit(limit);
		}

		List<Tuple> bookmarkRows = bookmarkQuery.fetch();

		if (bookmarkRows.isEmpty()) return List.of();

		Map<Long, Long> bookmarkIdMap = new LinkedHashMap<>();
		for (Tuple row : bookmarkRows) {
			Long contentId = row.get(contentBookmark.contentId);
			Long bookmarkId = row.get(contentBookmark.id);
			if (contentId != null && bookmarkId != null) {
				bookmarkIdMap.put(contentId, bookmarkId);
			}
		}

		List<Long> contentIds = new ArrayList<>(bookmarkIdMap.keySet());

		// 2) 컨텐츠 기본 정보 (year/bookmark_count NULL 행 방어를 위해 coalesce)
		List<Tuple> contentRows = jpaQueryFactory
			.select(content.id, content.title, content.year.coalesce(0), content.poster, content.bookmarkCount.coalesce(0))
			.from(content)
			.where(content.id.in(contentIds))
			.fetch();

		Map<Long, BookmarkedContentRow> contentMap = new LinkedHashMap<>();
		for (Tuple row : contentRows) {
			Long id = row.get(content.id);
			if (id == null) continue;

			Integer year = row.get(content.year.coalesce(0));
			Integer bookmarkCount = row.get(content.bookmarkCount.coalesce(0));
			contentMap.put(id, new BookmarkedContentRow(
				bookmarkIdMap.get(id),
				id,
				row.get(content.title),
				row.get(content.poster),
				year == null ? 0 : year,
				bookmarkCount == null ? 0 : bookmarkCount,
				new ArrayList<>()
			));
		}

		// 사용자가 구독 중인 OTT 중, 해당 컨텐츠를 제공하는 OTT만 추린다.
		// (단건 OTT 조회와 동일한 join 구조: OttUser → OttProvider → OttContent)
		List<Tuple> ottRows = jpaQueryFactory
			.selectDistinct(
				ottContent.contentId,
				ottProvider.name,
				ottProvider.logoUrl
			)
			.from(ottUser)
			.join(ottUser.ottProvider, ottProvider)
			.join(ottContent).on(ottContent.ottProvider.eq(ottProvider))
			.where(
				ottUser.userId.eq(userId),
				ottContent.contentId.in(contentIds)
			)
			.fetch();

		for (Tuple row : ottRows) {
			Long contentId = row.get(ottContent.contentId);
			if (contentId == null) continue;

			String ottName = row.get(ottProvider.name);
			String logoUrl = row.get(ottProvider.logoUrl);

			BookmarkedContentRow dto = contentMap.get(contentId);
			if (dto == null) continue;

			if (ottName != null) {
				dto.ottSimpleList().add(new GetContentDetailRes.GetOttSimpleRes(ottName, logoUrl));
			}
		}

		List<BookmarkedContentRow> result = new ArrayList<>();
		for (Long id : contentIds) {
			BookmarkedContentRow dto = contentMap.get(id);
			if (dto != null) result.add(dto);
		}
		return result;
	}

	public record BookmarkedContentRow(
		Long bookmarkId,
		Long contentId,
		String title,
		String imageUrl,
		int year,
		int bookmarkCount,
		List<GetContentDetailRes.GetOttSimpleRes> ottSimpleList
	) {
		public GetContentDetailRes toResponse() {
			return new GetContentDetailRes(
				contentId,
				title,
				imageUrl,
				year,
				bookmarkCount,
				ottSimpleList
			);
		}
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

	public List<ContentSearchRow> searchContents(ContentSearchCondition condition) {
		List<Long> genreIds = findGenreIds(condition.genreNames());
		if (condition.hasGenres() && genreIds.size() != condition.genreNames().size()) {
			return List.of();
		}

		return jpaQueryFactory
			.select(Projections.constructor(
				ContentSearchRow.class,
				content.id,
				content.title,
				content.author,
				content.poster,
				content.year,
				content.bookmarkCount
			))
			.from(content)
			.where(
				keywordCondition(condition),
				onCondition(condition.mediaType(), content.mediaType::eq),
				cursorCondition(condition),
				genreCondition(genreIds)
			)
			.orderBy(content.bookmarkCount.desc(), content.id.desc())
			.limit(condition.queryLimit())
			.fetch();
	}

	private List<Long> findGenreIds(List<String> genreNames) {
		if (genreNames.isEmpty()) {
			return List.of();
		}

		return jpaQueryFactory
			.select(genre.id)
			.from(genre)
			.where(onNotEmpty(genreNames, names -> genre.name.in(names)))
			.fetch();
	}

	private Predicate keywordCondition(ContentSearchCondition condition) {
		if (!condition.hasKeyword()) {
			return emptyCondition();
		}

		String fullTextKeyword = toFullTextKeyword(condition.keyword());
		if (condition.usesFullTextSearch() && StringUtils.hasText(fullTextKeyword)) {
			return Expressions.booleanTemplate(
				"match_against_boolean({0}, {1})",
				content.title,
				fullTextKeyword
			);
		}

		return content.title.contains(condition.keyword());
	}

	private Predicate cursorCondition(ContentSearchCondition condition) {
		return onCondition(condition.cursor(), cursor ->
			content.bookmarkCount.lt(cursor.bookmarkCount())
				.or(content.bookmarkCount.eq(cursor.bookmarkCount())
					.and(content.id.lt(cursor.contentId())))
		);
	}

	private Predicate genreCondition(List<Long> genreIds) {
		return onNotEmpty(genreIds, ids ->
			com.querydsl.jpa.JPAExpressions
				.select(contentGenre.genre.id.countDistinct())
				.from(contentGenre)
				.where(
					contentGenre.content.id.eq(content.id),
					contentGenre.genre.id.in(ids)
				)
				.eq((long) ids.size())
		);
	}

	private String toFullTextKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		return FULLTEXT_BOOLEAN_OPERATOR_PATTERN.matcher(keyword)
			.replaceAll(" ")
			.replaceAll("\\s+", " ")
			.trim();
	}
}
