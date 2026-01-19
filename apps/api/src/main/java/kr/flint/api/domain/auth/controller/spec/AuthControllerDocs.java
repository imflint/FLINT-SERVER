package kr.flint.api.domain.auth.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kr.flint.api.domain.auth.dto.request.LogoutReq;
import kr.flint.api.domain.auth.dto.request.RefreshTokenReq;
import kr.flint.api.domain.auth.dto.request.SignupReq;
import kr.flint.api.domain.auth.dto.request.SocialVerifyReq;
import kr.flint.api.domain.auth.dto.response.AuthTokenRes;
import kr.flint.api.domain.auth.dto.response.SocialVerifyRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "소셜 로그인 - 호주",
            description = "Authorization Code를 사용하여 소셜 로그인을 처리합니다. 기존 회원이면 JWT를 발급하고, 신규 회원이면 임시 토큰을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "소셜 로그인 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "소셜 인증 실패",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<SuccessResponse<SocialVerifyRes>> verifySocialCode(SocialVerifyReq request);

    @Operation(
            summary = "회원가입 - 호주",
            description = "임시 토큰과 사용자 정보로 회원가입을 완료합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    useReturnTypeSchema = true
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
    ResponseEntity<SuccessResponse<AuthTokenRes>> signup(SignupReq request);

    @Operation(
            summary = "토큰 갱신 - 호주",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 Refresh Token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<SuccessResponse<AuthTokenRes>> refreshTokens(RefreshTokenReq request);

    @Operation(
            summary = "로그아웃 - 호주",
            description = "현재 세션(기기)에서 로그아웃합니다. Refresh Token을 무효화합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh Token 누락",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<Void> logout(LogoutReq request, HttpServletRequest httpRequest);

    @Operation(
            summary = "모든 세션 로그아웃 - 호주",
            description = "모든 세션(기기)에서 로그아웃합니다. 사용자의 모든 Refresh Token을 무효화합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "모든 세션 로그아웃 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    ResponseEntity<Void> logoutAll(Long userId, HttpServletRequest httpRequest);
}
