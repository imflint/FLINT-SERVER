package kr.flint.api.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.api.domain.user.dto.response.CollectionWithUserProjection;
import kr.flint.collection.domain.Collection;

@Repository
public interface UserCollectionRepository extends JpaRepository<Collection, Long> {

    // 본인 조회용 (전체 컬렉션)
    @Query(value = """
        SELECT c.id, c.title, c.image, u.profile_image AS profileImage, u.nickname AS userName
        FROM collections c
        JOIN users u ON c.user_id = u.id
        WHERE c.user_id = :userId AND c.deleted_at IS NULL
        ORDER BY c.id DESC
    """, nativeQuery = true)
    List<CollectionWithUserProjection> findAllCollectionsWithUserByUserId(@Param("userId") Long userId);

    // 타인 조회용 (공개 컬렉션만)
    @Query(value = """
        SELECT c.id, c.title, c.image, u.profile_image AS profileImage, u.nickname AS userName
        FROM collections c
        JOIN users u ON c.user_id = u.id
        WHERE c.user_id = :userId AND c.is_public = true AND c.deleted_at IS NULL
        ORDER BY c.id DESC
    """, nativeQuery = true)
    List<CollectionWithUserProjection> findPublicCollectionsWithUserByUserId(@Param("userId") Long userId);

    // 본인 북마크 조회용 (전체)
    @Query(value = """
        SELECT c.id, c.title, c.image, u.profile_image AS profileImage, u.nickname AS userName
        FROM collections c
        JOIN users u ON c.user_id = u.id
        WHERE c.id IN :collectionIds AND c.deleted_at IS NULL
        ORDER BY c.id DESC
    """, nativeQuery = true)
    List<CollectionWithUserProjection> findAllCollectionsWithUserByIdIn(@Param("collectionIds") List<Long> collectionIds);

    // 타인 북마크 조회용 (공개만)
    @Query(value = """
        SELECT c.id, c.title, c.image, u.profile_image AS profileImage, u.nickname AS userName
        FROM collections c
        JOIN users u ON c.user_id = u.id
        WHERE c.id IN :collectionIds AND c.is_public = true AND c.deleted_at IS NULL
        ORDER BY c.id DESC
    """, nativeQuery = true)
    List<CollectionWithUserProjection> findPublicCollectionsWithUserByIdIn(@Param("collectionIds") List<Long> collectionIds);
}
