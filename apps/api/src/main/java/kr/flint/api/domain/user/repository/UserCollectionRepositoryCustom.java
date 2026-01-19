package kr.flint.api.domain.user.repository;

import java.util.List;

import kr.flint.api.domain.user.dto.response.CollectionContentImageDto;
import kr.flint.api.domain.user.dto.response.CollectionWithUserDto;

public interface UserCollectionRepositoryCustom {

	// 본인 조회용 (전체 컬렉션)
	List<CollectionWithUserDto> findAllCollectionsWithUserByUserId(Long userId);

	// 타인 조회용 (공개 컬렉션만)
	List<CollectionWithUserDto> findPublicCollectionsWithUserByUserId(Long userId);

	// 본인 북마크 조회용 (전체)
	List<CollectionWithUserDto> findAllCollectionsWithUserByIdIn(List<Long> collectionIds);

	// 타인 북마크 조회용 (공개만)
	List<CollectionWithUserDto> findPublicCollectionsWithUserByIdIn(List<Long> collectionIds);

	// 컬렉션별 콘텐츠 이미지 조회
	List<CollectionContentImageDto> findContentImagesByCollectionIds(List<Long> collectionIds);
}
