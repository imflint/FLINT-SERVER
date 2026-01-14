package kr.flint.api.domain.home.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;
import kr.flint.collection.domain.Collection;

@Repository
public interface HomeCollectionRepository extends JpaRepository<Collection, Long> {

    // 컬렉션 카드 정보 조회 (컬렉션 + 작성자 정보 JOIN)
    @Query("""
        SELECT c.id as id, c.title as title, c.image as image,
               u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.id IN :collectionIds
        AND c.isPublic = true
    """)
    List<CollectionCardProjection> findCollectionCardsWithUser(@Param("collectionIds") List<Long> collectionIds);

    // 활성 Fliner ID 목록 조회
    @Query("SELECT u.id FROM User u WHERE u.userRole = 'FLINER' AND u.status = 'ACTIVE'")
    List<Long> findAllFlinerIds();

    // Fliner들의 공개 컬렉션 조회
    @Query("""
        SELECT c.id as id, c.userId as userId, c.createdAt as createdAt
        FROM Collection c
        WHERE c.userId IN :flinerIds
        AND c.isPublic = true
    """)
    List<CollectionBasicProjection> findPublicCollectionsByFlinerIds(@Param("flinerIds") List<Long> flinerIds);
}
