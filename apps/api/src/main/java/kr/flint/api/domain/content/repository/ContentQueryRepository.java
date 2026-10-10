package kr.flint.api.domain.content.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.content.domain.QGenre.*;
import static kr.flint.ott.domain.QOttContent.*;
import static kr.flint.ott.domain.QOttProvider.*;
import static kr.flint.shared.util.QueryDslUtil.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.search.dto.response.GetSearchBookmarkContentRes;
import kr.flint.content.domain.GenreCode;

@Repository
public class ContentQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;
	private final ContentSearchNativeRepository contentSearchNativeRepository;

	public boolean localizedSearchEnabled() {
		return contentSearchNativeRepository.localizedSearchEnabled();
	}

	public boolean searchReadModelEnabled() {
		return contentSearchNativeRepository.searchReadModelEnabled();
	}

	public ContentQueryRepository(
		JPAQueryFactory jpaQueryFactory,
		ContentSearchNativeRepository contentSearchNativeRepository
	) {
		this.jpaQueryFactory = jpaQueryFactory;
		this.contentSearchNativeRepository = contentSearchNativeRepository;
	}

	public record ContentSearchRow(
		Long id,
		String title,
		String author,
		String posterUrl,
		int year,
		int bookmarkCount,
		int exactMatchRank,
		double relevanceScore
	) {
		public ContentSearchRow(
			Long id,
			String title,
			String author,
			String posterUrl,
			int year,
			int bookmarkCount
		) {
			this(id, title, author, posterUrl, year, bookmarkCount, 0, 0.0);
		}

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
			.select(content.id, content.title, content.author, content.year.coalesce(0), content.poster, content.bookmarkCount.coalesce(0))
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
				normalizeAuthor(row.get(content.author)),
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
				ottProvider.logoUrl,
				ottProvider.displayPriority,
				ottProvider.id
			)
			.from(ottContent)
			.join(ottContent.ottProvider, ottProvider)
			.where(
				ottContent.contentId.in(contentIds),
				ottProvider.active.isTrue()
			)
			.orderBy(
				ottContent.contentId.asc(),
				ottProvider.displayPriority.asc(),
				ottProvider.id.asc()
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
		String author,
		String imageUrl,
		int year,
		int bookmarkCount,
		List<GetContentDetailRes.GetOttSimpleRes> ottSimpleList
	) {
		public GetContentDetailRes toResponse() {
			return new GetContentDetailRes(
				contentId,
				title,
				author,
				imageUrl,
				year,
				bookmarkCount,
				ottSimpleList
			);
		}
	}

	private String normalizeAuthor(String author) {
		return StringUtils.hasText(author) && !"Unknown".equalsIgnoreCase(author.trim())
			? author.trim()
			: null;
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
			.leftJoin(ottContent.ottProvider, ottProvider).on(ottProvider.active.isTrue())
			.where(
				content.title.contains(keyword)
			)
			.orderBy(
				contentBookmark.createdAt.desc(),
				ottProvider.displayPriority.asc(),
				ottProvider.id.asc()
			)
			.limit(10)
			.fetch();

		Map<Long, GetSearchBookmarkContentRes> contentMap = new LinkedHashMap<>();
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
		Long genreId = findGenreId(condition.genreCode());
		if (condition.hasGenre() && genreId == null) {
			return List.of();
		}

		if (condition.hasKeyword() || genreId != null) {
			return contentSearchNativeRepository.search(condition, genreId).stream()
				.map(ContentSearchProjection::toSearchRow)
				.toList();
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
				onCondition(condition.mediaType(), content.mediaType::eq),
				onCondition(condition.cursor(), cursor -> content.bookmarkCount.lt(cursor.bookmarkCount())
					.or(content.bookmarkCount.eq(cursor.bookmarkCount()).and(content.id.lt(cursor.contentId()))))
			)
			.orderBy(content.bookmarkCount.desc(), content.id.desc())
			.limit(condition.queryLimit())
			.fetch();
	}

	private Long findGenreId(GenreCode genreCode) {
		if (genreCode == null) {
			return null;
		}

		return jpaQueryFactory
			.select(genre.id)
			.from(genre)
			.where(genre.code.eq(genreCode))
			.fetchOne();
	}

}
