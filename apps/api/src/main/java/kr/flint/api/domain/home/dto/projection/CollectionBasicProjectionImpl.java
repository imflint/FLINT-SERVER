package kr.flint.api.domain.home.dto.projection;

import java.time.LocalDateTime;

public record CollectionBasicProjectionImpl(
    Long id,
    Long userId,
    LocalDateTime createdAt
) implements CollectionBasicProjection {

    @Override
    public Long getId() { return id; }

    @Override
    public Long getUserId() { return userId; }

    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }
}
