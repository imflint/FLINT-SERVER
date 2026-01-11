package kr.flint.api.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Component;

import kr.flint.content.service.ContentService;
import kr.flint.ott.dto.GetOttResponse;
import kr.flint.ott.service.OttService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContentQueryFacade {
	private final OttService ottService;

	public List<GetOttResponse> getOttList(final Long userId, final Long contentId) {
		return ottService.getOttList(userId, contentId);
	}
}
