package kr.flint.api.domain.collection.dto.request;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.collection.dto.CollectionUpdateCommand;

@Schema(description = "컬렉션 수정 요청 (전체 교체 — 작품 리스트도 통째로 대체)")
public record UpdateCollectionReq(
	@Schema(description = "컬렉션 대표 이미지 S3 key 또는 URL (선택, 미지정 시 null)", example = "collection/thumbnail/abc123.jpg")
	@Nullable
	String imageUrl,

	@Schema(description = "컬렉션 제목 (최대 20자)", example = "주말에 보기 좋은 영화", maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "컬렉션 제목은 필수 입력 값입니다")
	@Size(max = 20)
	String title,

	@Schema(description = "컬렉션 설명 (최대 200자)", example = "주말에 편하게 볼 수 있는 영화들을 모았습니다.", maxLength = 200)
	@Size(max = 200)
	String description,

	@Schema(description = "공개 여부 (true: 공개, false: 비공개)", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "공개여부는 필수 입력값입니다")
	Boolean isPublic,

	@Schema(description = "수정 후 컬렉션에 포함될 작품 목록 (기존 목록은 통째로 대체됨)", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "작품은 필수 입력값입니다")
	@Size(min = 1, message = "작품은 1개 이상 입력해야 합니다")
	List<@NotNull(message = "작품 정보는 필수 입력값입니다") @Valid AddContentReq> contentList
	) {

	@AssertTrue(message = "작품은 중복 없이 입력해야 합니다")
	public boolean isUniqueContentIds() {
		if (contentList == null) {
			return true;
		}

		Set<Long> contentIds = new HashSet<>();
		for (AddContentReq content : contentList) {
			if (content == null || content.contentId() == null) {
				continue;
			}
			if (!contentIds.add(content.contentId())) {
				return false;
			}
		}
		return true;
	}

	public CollectionUpdateCommand toCommand() {
		return CollectionUpdateCommand.of(
			title,
			description,
			imageUrl,
			isPublic,
			contentList.stream()
				.map(AddContentReq::toInput)
				.toList()
		);
	}
}
