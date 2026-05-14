package kr.flint.terms.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TermsContext {
	SIGNUP("회원가입"),
	WITHDRAWAL("회원탈퇴");

	private final String description;
}
