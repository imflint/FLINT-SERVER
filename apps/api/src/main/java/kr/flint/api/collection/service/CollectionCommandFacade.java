package kr.flint.api.collection.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.collection.dto.request.CreateCollectionReq;
import kr.flint.collection.service.CollectionService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CollectionCommandFacade {
	private final CollectionService collectionService;
	private final UserService userService;

	@Transactional
	public void createCollection(final Long userId, final CreateCollectionReq createCollectionReq) {
		userService.getById(userId);
		collectionService.createCollection(userId, createCollectionReq);
	}
}
