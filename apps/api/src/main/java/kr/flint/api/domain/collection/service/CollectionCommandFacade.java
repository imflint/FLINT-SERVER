package kr.flint.api.domain.collection.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.domain.Content;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionCommandFacade {
	private final CollectionService collectionService;
	private final UserService userService;
	private final ContentService contentService;

	@Transactional
	public void createCollection(final Long userId, final CreateCollectionReq request) {
		userService.getById(userId);
		Long contentId = request.contentList().get(0).contentId();
		Content content = contentService.getContentById(contentId);
		String contentImageUrl = content.getPoster();
		collectionService.createCollection(userId, request.toCommand(), contentImageUrl);
	}
}
