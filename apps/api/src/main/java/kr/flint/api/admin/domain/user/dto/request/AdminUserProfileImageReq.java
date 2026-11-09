package kr.flint.api.admin.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 플리너 사진 변경 요청")
public record AdminUserProfileImageReq(
    @Schema(description = "업로드를 완료한 프로필 이미지 key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "프로필 이미지 key는 필수입니다.")
    @Size(max = 255, message = "프로필 이미지 key는 255자 이하여야 합니다.")
    @Pattern(
        regexp = "user/profile/\\d{6}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)",
        message = "프로필 이미지 업로드로 발급받은 key를 입력해주세요."
    )
    String profileImage
) {
}
