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

    public static Keyword create(String name) {
        return Keyword.builder()
            .name(name)
            .build();
    }
}
