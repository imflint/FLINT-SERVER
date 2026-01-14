package kr.flint.api.domain.search.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.search.dto.GetContentSearchRes;
import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.api.domain.search.dto.GetSearchBookmarkContentRes;
import kr.flint.content.domain.Content;
import kr.flint.content.service.ContentService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchQueryFacade {
	private final ContentService contentService;
	private final ContentQueryRepository contentQueryRepository;

	public List<GetContentSearchRes> searchContent(final String keyword){
		if(keyword == null || keyword.isEmpty()){
			List<Content> contentSearchResList = contentService.getAllContent();
			return contentSearchResList.stream()
				.map(GetContentSearchRes::from)
				.toList();
		}
		List<Content> contentList = contentService.getContentByTitle(keyword);
		return contentList.stream()
			.map(GetContentSearchRes::from)
			.toList();
	}

	public List<GetSearchBookmarkContentRes> searchBookmarkContent(final Long userId, final String keyword){
		return contentQueryRepository.getSearchBookmarkContent(userId, keyword);
	}

}
