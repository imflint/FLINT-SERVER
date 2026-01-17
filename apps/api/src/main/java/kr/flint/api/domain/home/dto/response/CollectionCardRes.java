package kr.flint.api.domain.home.dto.response;

import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;

@Schema(description = "컬렉션 카드 정보")
public record CollectionCardRes(
    @Schema(description = "컬렉션 ID", example = "123456789")
    String id,

    @Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 영화")
    String title,

    @Schema(description = "컬렉션 대표 이미지 URL")
    String image,

    @Schema(description = "생성자 프로필 이미지 URL")
    String profileImage,

    @Schema(description = "생성자 닉네임", example = "영화덕후")
    String userName
) {
    public static CollectionCardRes from(CollectionCardProjection projection, Function<String, String> imageUrlResolver) {
        return new CollectionCardRes(
            String.valueOf(projection.getId()),
            projection.getTitle(),
            imageUrlResolver.apply(projection.getImage()),
            projection.getProfileImage(),
            projection.getUserName()
        );
    }
}
