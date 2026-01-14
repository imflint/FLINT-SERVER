package kr.flint.api.domain.home.dto.projection;

import java.time.LocalDateTime;

public interface CollectionBasicProjection {
    Long getId();
    Long getUserId();
    LocalDateTime getCreatedAt();
}
