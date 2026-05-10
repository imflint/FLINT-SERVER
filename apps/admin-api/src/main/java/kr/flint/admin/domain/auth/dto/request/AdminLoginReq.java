package kr.flint.admin.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginReq(
	@Schema(description = "관리자 로그인 ID", example = "admin")
	@NotBlank(message = "username은 필수입니다")
	String username,

	@Schema(description = "관리자 비밀번호", example = "password")
	@NotBlank(message = "password는 필수입니다")
	String password
) {
}
