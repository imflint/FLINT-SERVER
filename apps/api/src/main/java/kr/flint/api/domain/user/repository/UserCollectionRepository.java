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
    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.userId = :userId
    """)
    List<CollectionWithUserProjection> findAllCollectionsWithUserByUserId(@Param("userId") Long userId);

    // 타인 조회용 (공개 컬렉션만)
    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.userId = :userId AND c.isPublic = true
    """)
    List<CollectionWithUserProjection> findPublicCollectionsWithUserByUserId(@Param("userId") Long userId);

    // 본인 북마크 조회용 (전체)
    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.id IN :collectionIds
    """)
    List<CollectionWithUserProjection> findAllCollectionsWithUserByIdIn(@Param("collectionIds") List<Long> collectionIds);

    // 타인 북마크 조회용 (공개만)
    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.id IN :collectionIds AND c.isPublic = true
    """)
    List<CollectionWithUserProjection> findPublicCollectionsWithUserByIdIn(@Param("collectionIds") List<Long> collectionIds);
}
