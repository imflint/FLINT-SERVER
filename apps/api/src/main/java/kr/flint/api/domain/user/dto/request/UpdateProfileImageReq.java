package kr.flint.api.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "프로필 이미지 수정 요청")
public record UpdateProfileImageReq(
		@Schema(description = "S3에 업로드된 프로필 이미지 키", example = "user/profile/01J5K3...")
		@NotBlank(message = "프로필 이미지 키는 필수입니다.")
		@JsonAlias("profileImageUrl") // 클라이언트가 profileImageUrl로 보내는 경우 호환
		String profileImage
) {
}
