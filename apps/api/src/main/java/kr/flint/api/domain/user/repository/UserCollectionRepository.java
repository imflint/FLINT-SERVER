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

    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.userId = :userId
    """)
    List<CollectionWithUserProjection> findCollectionsWithUserByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT c.id as id, c.title as title, c.image as image, u.profileImage as profileImage, u.nickname as userName
        FROM Collection c
        JOIN User u ON c.userId = u.id
        WHERE c.id IN :collectionIds
    """)
    List<CollectionWithUserProjection> findCollectionsWithUserByIdIn(@Param("collectionIds") List<Long> collectionIds);
}
