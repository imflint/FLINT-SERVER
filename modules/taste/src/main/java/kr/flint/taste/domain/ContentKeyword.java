package kr.flint.taste.domain;

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
    name = "content_keywords",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_content_keyword",
            columnNames = {"content_id", "keyword_id"}
        )
    }
)
public class ContentKeyword extends Base {

    @Column(nullable = false)
    private Long contentId;

    @Column(nullable = false)
    private Long keywordId;

    private Double confidence;

    public static ContentKeyword create(Long contentId, Long keywordId) {
        return ContentKeyword.builder()
            .contentId(contentId)
            .keywordId(keywordId)
            .build();
    }

    public static ContentKeyword create(Long contentId, Long keywordId, Double confidence) {
        return ContentKeyword.builder()
            .contentId(contentId)
            .keywordId(keywordId)
            .confidence(confidence)
            .build();
    }
}
