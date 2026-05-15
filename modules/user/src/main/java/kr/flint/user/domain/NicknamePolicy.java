package kr.flint.user.domain;

import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;

public final class NicknamePolicy {

	public static final int MIN_LENGTH = 2;
	public static final int MAX_LENGTH = 8;
	public static final String REGEX = "^[a-zA-Z0-9가-힣]+$";
	public static final String MESSAGE = "닉네임은 2자 이상 8자 이하이며 한글, 영문, 숫자만 사용할 수 있습니다.";

	private NicknamePolicy() {
	}

	public static void validate(String nickname) {
		if (nickname == null
			|| nickname.isBlank()
			|| nickname.length() < MIN_LENGTH
			|| nickname.length() > MAX_LENGTH
			|| !nickname.matches(REGEX)
		) {
			throw new UserException(UserErrorCode.INVALID_NICKNAME);
		}
	}
}
