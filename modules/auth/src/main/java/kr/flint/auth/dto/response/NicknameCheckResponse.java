package kr.flint.auth.dto.response;

public record NicknameCheckResponse(
        boolean available
) {
    public static NicknameCheckResponse of(boolean available) {
        return new NicknameCheckResponse(available);
    }
}
