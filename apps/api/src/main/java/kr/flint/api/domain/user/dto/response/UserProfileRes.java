package kr.flint.api.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.user.domain.User;
import kr.flint.user.domain.UserRole;

@Schema(description = "사용자 프로필 응답")
public record UserProfileRes(
    @Schema(description = "사용자 ID", example = "123456789", type = "string")
    String id,
    @Schema(description = "닉네임", example = "홍길동")
    String nickname,
    @Schema(description = "프로필 이미지 URL")
    String profileImageUrl,
    @Schema(description = "플리너 여부", example = "false")
    boolean isFliner
) {
    public static UserProfileRes from(User user) {
        return new UserProfileRes(
            String.valueOf(user.getId()),
            user.getNickname(),
            user.getProfileImage(),
            user.getUserRole() == UserRole.FLINER
        );
    }
}
