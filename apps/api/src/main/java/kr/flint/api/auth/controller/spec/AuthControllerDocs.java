package kr.flint.api.auth.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kr.flint.api.config.security.UserPrincipal;
import kr.flint.auth.dto.request.LogoutRequest;
import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.NicknameCheckResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "소셜 로그인",
            description = "Authorization Code를 사용하여 소셜 로그인을 처리합니다. 기존 회원이면 JWT를 발급하고, 신규 회원이면 임시 토큰을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "소셜 로그인 성공",
                    content = @Content(schema = @Schema(implementation = SocialVerifySuccessResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "소셜 인증 실패",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<SuccessResponse<SocialVerifyResponse>> verifySocialCode(SocialVerifyRequest request);

    @Operation(
            summary = "닉네임 중복 체크",
            description = "닉네임 사용 가능 여부를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "닉네임 체크 성공",
                    content = @Content(schema = @Schema(implementation = NicknameCheckSuccessResponse.class))
            )
    })
    ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @Parameter(description = "확인할 닉네임", example = "my_nickname") String nickname
    );

    @Operation(
            summary = "회원가입",
            description = "임시 토큰과 사용자 정보로 회원가입을 완료합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = AuthTokenSuccessResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 임시 토큰",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 닉네임",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<SuccessResponse<AuthTokenResponse>> signup(SignupRequest request);

    @Operation(
            summary = "토큰 갱신",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = @Content(schema = @Schema(implementation = AuthTokenSuccessResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 Refresh Token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<SuccessResponse<AuthTokenResponse>> refreshTokens(RefreshTokenRequest request);

    @Operation(
            summary = "로그아웃",
            description = "현재 Refresh Token을 무효화합니다. Refresh Token을 전달하면 해당 토큰만 삭제하고, 전달하지 않으면 모든 세션에서 로그아웃됩니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<Void> logout(UserPrincipal principal, LogoutRequest request, HttpServletRequest httpRequest);

    // Swagger 문서용 응답 타입 정의
    @Schema(description = "소셜 로그인 응답")
    record SocialVerifySuccessResponse(
            @Schema(description = "HTTP 상태 코드", example = "200") int status,
            @Schema(description = "응답 메시지", example = "로그인이 완료되었습니다") String message,
            @Schema(description = "응답 데이터") SocialVerifyResponse data
    ) {}

    @Schema(description = "닉네임 체크 응답")
    record NicknameCheckSuccessResponse(
            @Schema(description = "HTTP 상태 코드", example = "200") int status,
            @Schema(description = "응답 메시지", example = "닉네임 확인이 완료되었습니다") String message,
            @Schema(description = "응답 데이터") NicknameCheckResponse data
    ) {}

    @Schema(description = "인증 토큰 응답")
    record AuthTokenSuccessResponse(
            @Schema(description = "HTTP 상태 코드", example = "200") int status,
            @Schema(description = "응답 메시지", example = "토큰이 갱신되었습니다") String message,
            @Schema(description = "응답 데이터") AuthTokenResponse data
    ) {}
}
