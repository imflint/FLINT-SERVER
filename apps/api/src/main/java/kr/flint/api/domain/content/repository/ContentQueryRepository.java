package kr.flint.api.domain.content.repository;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.ott.domain.QOttContent.*;
import static kr.flint.ott.domain.QOttProvider.*;
import static kr.flint.ott.domain.QOttUser.*;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.group.GroupBy;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.content.dto.GetContentDetailRes;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<GetContentDetailRes> getContentDetailList(Long userId){
		return jpaQueryFactory
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
				ottUser.ottProvider.eq(ottContent.ottProvider)
			)
			.transform(
				GroupBy.groupBy(content.id).list(
					Projections.constructor(
						GetContentDetailRes.class,
						content.id,
						content.title,
						content.year,
						GroupBy.list(
							Projections.constructor(
								GetContentDetailRes.GetOttSimpleRes.class,
								ottProvider.id,
								ottProvider.logoUrl
							)
						)
					)
				)
			);

	}
}
