package kr.flint.api.domain.collection.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.service.CollectionService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CollectionCommandFacade {
	private final CollectionService collectionService;
	private final UserService userService;

	@Transactional
	public void createCollection(final Long userId, final CreateCollectionReq request) {
		userService.getById(userId);
		collectionService.createCollection(userId, request.toCommand());
	}
}
