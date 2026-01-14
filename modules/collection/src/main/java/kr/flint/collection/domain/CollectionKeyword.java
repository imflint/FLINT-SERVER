package kr.flint.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.flint.shared.domain.Base;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
    name = "collection_keywords",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_collection_keyword",
            columnNames = {"collection_id", "keyword_id"}
        )
    }
)
public class CollectionKeyword extends Base {

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "keyword_id", nullable = false)
    private Long keywordId;

    public static CollectionKeyword create(Long collectionId, Long keywordId) {
        return CollectionKeyword.builder()
            .collectionId(collectionId)
            .keywordId(keywordId)
            .build();
    }
}
