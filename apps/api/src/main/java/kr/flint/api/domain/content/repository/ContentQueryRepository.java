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
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.Tuple;
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
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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

	public List<ContentSearchRow> searchContents(ContentSearchCondition condition) {
		StringBuilder sql = new StringBuilder("""
			SELECT c.id,
			       c.title,
			       c.author,
			       c.poster AS poster_url,
			       c.year,
			       c.bookmark_count
			FROM content c
			""");
		MapSqlParameterSource params = new MapSqlParameterSource();

		if (condition.hasGenres()) {
			sql.append("""
				JOIN (
				    SELECT cg.content_id
				    FROM content_genre cg
				    JOIN genre g ON g.id = cg.genre_id
				    WHERE g.name IN (:genreNames)
				    GROUP BY cg.content_id
				    HAVING COUNT(DISTINCT g.name) = :genreCount
				) matched_genre ON matched_genre.content_id = c.id
				""");
			params.addValue("genreNames", condition.genreNames());
			params.addValue("genreCount", condition.genreNames().size());
		}

		sql.append("WHERE 1 = 1\n");
		if (condition.hasKeyword()) {
			String fullTextKeyword = toFullTextKeyword(condition.keyword());
			if (condition.usesFullTextSearch() && StringUtils.hasText(fullTextKeyword)) {
				sql.append("AND MATCH(c.title) AGAINST (:keyword IN BOOLEAN MODE)\n");
				params.addValue("keyword", fullTextKeyword);
			} else {
				sql.append("AND c.title LIKE CONCAT('%', :keyword, '%')\n");
				params.addValue("keyword", condition.keyword());
			}
		}
		if (condition.mediaType() != null) {
			sql.append("AND c.media_type = :mediaType\n");
			params.addValue("mediaType", condition.mediaType().name());
		}

		sql.append("ORDER BY c.bookmark_count DESC, c.id DESC\n");
		sql.append("LIMIT :limit OFFSET :offset");
		params.addValue("limit", condition.queryLimit());
		params.addValue("offset", condition.offset());

		return namedParameterJdbcTemplate.query(
			sql.toString(),
			params,
			(rs, rowNum) -> new ContentSearchRow(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("author"),
				rs.getString("poster_url"),
				rs.getInt("year"),
				rs.getInt("bookmark_count")
			)
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
