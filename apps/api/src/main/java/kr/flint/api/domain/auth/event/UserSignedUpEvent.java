package kr.flint.api.domain.auth.event;

import java.util.List;

public record UserSignedUpEvent(
    Long userId,
    List<Long> contentIds
) {
    public static UserSignedUpEvent of(Long userId, List<Long> contentIds) {
        return new UserSignedUpEvent(userId, contentIds);
    }
}
