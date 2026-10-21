package kr.flint.taste.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "keywords",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_keyword_name",
            columnNames = {"name"}
        )
    }
)
public class Keyword extends Base {

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeywordLevel level;

    @Column
    private String image;

    public static Keyword create(String name, KeywordLevel level) {
        return Keyword.builder()
            .name(name)
            .level(level)
            .build();
    }

    public static Keyword create(String name, KeywordLevel level, String image) {
        return Keyword.builder()
            .name(name)
            .level(level)
            .image(image)
            .build();
    }

    // 카탈로그(KeywordCatalog)가 정본이므로 레벨이 어긋난 행은 카탈로그 값으로 보정한다
    public void changeLevel(KeywordLevel level) {
        this.level = level;
    }

    public boolean hasLevel(KeywordLevel level) {
        return this.level == level;
    }

    /** 등록된 아이콘이 없으면 레벨 기본 아이콘 키를 돌려준다. 절대 null 이 아니다. */
    public String resolveIconKey() {
        return hasImage() ? image : level.getDefaultIconKey();
    }

    private boolean hasImage() {
        return image != null && !image.isBlank();
    }

}
