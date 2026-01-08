package kr.flint.collection.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCollectionReq(
	@NotNull(message = "컬렉션 이미지는 필수 입력값입니다")
	String imageUrl,
	@NotNull(message = "컬렉션 제목은 필수 입력 값입니다")
	@Size(min = 0, max = 20)
	String title,
	@Size(max = 200)
	String description,
	@NotNull(message = "공개여부는 필수 입력값입니다")
	boolean isPublic,
	@NotNull(message = "작품은 필수 입력값입니다")
	List<AddContentReq> contentList
) {
}
