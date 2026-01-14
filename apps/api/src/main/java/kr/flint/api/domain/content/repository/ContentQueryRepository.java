package kr.flint.api.domain.content.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.ott.domain.QOttContent.*;
import static kr.flint.ott.domain.QOttProvider.*;
import static kr.flint.ott.domain.QOttUser.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.group.GroupBy;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.search.dto.GetSearchBookmarkContentRes;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<GetContentDetailRes> getContentDetailList(Long userId){
		List<Tuple> rows= jpaQueryFactory
			.select(
				content.id,
				content.title,
				content.year,
				ottProvider.id,
				ottProvider.logoUrl
			)
			.from(content)
			.join(contentBookmark).on(
				contentBookmark.contentId.eq(content.id),
				contentBookmark.userId.eq(userId)
			)
			.join(ottContent).on(
				ottContent.contentId.eq(content.id)
			)
			.join(ottContent.ottProvider, ottProvider)
			.join(ottUser).on(
				ottUser.userId.eq(userId),
				ottUser.ottProvider.eq(ottProvider)
			)
			.orderBy(contentBookmark.createdAt.desc())
			.limit(10)
			.fetch();

		Map<Long, GetContentDetailRes> contentMap = new LinkedHashMap<>();
		Map<Long, List<GetContentDetailRes.GetOttSimpleRes>> ottMap = new LinkedHashMap<>();
		for (Tuple row : rows) {
			Long contentId = row.get(content.id);
			if (contentId == null) continue;
			String title = row.get(content.title);
			int year = row.get(content.year);

			Long ottId = row.get(ottProvider.id);
			String logoUrl = row.get(ottProvider.logoUrl);

			contentMap.computeIfAbsent(contentId, id ->
				new GetContentDetailRes(
					id,
					title,
					year,
					new ArrayList<>()
				)
			);

			GetContentDetailRes dto = contentMap.get(contentId);
			if (ottId != null) {
				dto.getOttSimpleList().add(new GetContentDetailRes.GetOttSimpleRes(ottId, logoUrl));
			}

		}

		return new ArrayList<>(contentMap.values());
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
}
