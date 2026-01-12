package kr.flint.api.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.content.service.ContentService;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.ott.service.OttService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentQueryFacade {
	private final OttService ottService;
	private final ContentQueryRepository contentQueryRepository;

	public List<GetOttResponse> getOttList(final Long userId, final Long contentId) {
		return ottService.getOttList(userId, contentId);
	}

	public List<GetContentDetailRes> getContentDetailList(final Long userId) {
		return contentQueryRepository.getContentDetailList(userId);
	}
}
