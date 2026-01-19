package kr.flint.api.domain.user.repository;

import java.util.List;

import kr.flint.api.domain.user.dto.response.CollectionContentImageProjection;
import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;

public interface UserCollectionRepositoryCustom {

	// 본인 조회용 (전체 컬렉션)
	List<CollectionWithUserProjection> findAllCollectionsWithUserByUserId(Long userId);

	// 타인 조회용 (공개 컬렉션만)
	List<CollectionWithUserProjection> findPublicCollectionsWithUserByUserId(Long userId);

	// 본인 북마크 조회용 (전체)
	List<CollectionWithUserProjection> findAllCollectionsWithUserByIdIn(List<Long> collectionIds);

	// 타인 북마크 조회용 (공개만)
	List<CollectionWithUserProjection> findPublicCollectionsWithUserByIdIn(List<Long> collectionIds);

	// 컬렉션별 콘텐츠 이미지 조회
	List<CollectionContentImageProjection> findContentImagesByCollectionIds(List<Long> collectionIds);
}
