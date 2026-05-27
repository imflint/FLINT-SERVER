package kr.flint.api.domain.collection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;

class CollectionQueryRepositoryUnitTest {

	@Test
	@DisplayName("탐색 컬렉션 목록 응답은 선택된 콘텐츠 row의 이미지, 제목, 추천 이유를 함께 사용")
	void simpleResponseUsesSelectedContentRow() {
		// given
		CollectionQueryRepository.CollectionSimpleRow collection =
			new CollectionQueryRepository.CollectionSimpleRow(1L);
		CollectionQueryRepository.CollectionContentInfoRow selectedContent =
			new CollectionQueryRepository.CollectionContentInfoRow(
				1L,
				"custom-image.jpg",
				"poster.jpg",
				"선택된 콘텐츠",
				"선택된 콘텐츠 추천 이유입니다"
			);

		// when
		GetCollectionSimpleRes response =
			CollectionQueryRepository.toSimpleResponse(collection, selectedContent);

		// then
		assertThat(response.collectionId()).isEqualTo(1L);
		assertThat(response.imageUrl()).isEqualTo("custom-image.jpg");
		assertThat(response.contentTitle()).isEqualTo("선택된 콘텐츠");
		assertThat(response.contentDescription()).isEqualTo("선택된 콘텐츠 추천 이유입니다");
	}

	@Test
	@DisplayName("선택된 콘텐츠 row에 커스텀 이미지가 없으면 포스터를 사용")
	void selectedContentImageFallsBackToPoster() {
		// given
		CollectionQueryRepository.CollectionContentInfoRow selectedContent =
			new CollectionQueryRepository.CollectionContentInfoRow(
				1L,
				" ",
				"poster.jpg",
				"선택된 콘텐츠",
				"선택된 콘텐츠 추천 이유입니다"
			);

		// when & then
		assertThat(selectedContent.image()).isEqualTo("poster.jpg");
	}
}
