package kr.flint.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 중복 체크 응답")
public record NicknameCheckResponse(
        @Schema(description = "사용 가능 여부", example = "true")
        boolean available
) {
    public static NicknameCheckResponse of(boolean available) {
        return new NicknameCheckResponse(available);
    }
}
