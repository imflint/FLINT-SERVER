package kr.flint.api.domain.user.dto.response;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.dto.response.TermsAgreementStatusRes;
import kr.flint.user.domain.User;
import kr.flint.user.domain.UserRole;

@Schema(description = "내 프로필 응답")
public record MyProfileRes(
	@Schema(description = "사용자 ID", example = "123456789", type = "string")
	String id,
	@Schema(description = "닉네임", example = "홍길동")
	String nickname,
	@Schema(description = "이메일 (미보유 시 null)", example = "user@example.com", nullable = true)
	String email,
	@Schema(description = "프로필 이미지 URL")
	String profileImageUrl,
	@Schema(description = "플리너 여부", example = "false")
	boolean isFliner,
	@Schema(description = "GPT 취향 키워드 재계산 가능 여부 (마지막 재계산 이후 신규 북마크 20개 이상 누적 시 true)", example = "false")
	boolean keywordRecalculatable,
	@Schema(description = "약관 동의 상태")
	TermsAgreementStatusRes termsAgreementStatus
) {
	public static MyProfileRes from(
		User user,
		List<Terms> pendingRequiredTerms,
		Function<String, String> imageUrlResolver
	) {
		return new MyProfileRes(
			String.valueOf(user.getId()),
			user.getNickname(),
			null, // email: 현재 저장하지 않으므로 항상 null
			imageUrlResolver.apply(user.getProfileImage()),
			user.getUserRole() == UserRole.FLINER,
			user.isKeywordRecalculatable(),
			TermsAgreementStatusRes.from(pendingRequiredTerms)
		);
	}
}
