package kr.flint.terms.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TermsType {
	SERVICE("서비스 이용약관", true),
	PRIVACY("개인정보 처리방침", true),
	MARKETING("마케팅 정보 수신", false),
	WITHDRAWAL("회원탈퇴 약관", true);

	private final String description;
	private final boolean defaultRequired;
}
