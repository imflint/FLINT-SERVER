package kr.flint.api.domain.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.repository.CollectionQueryRepository;
import kr.flint.collection.service.CollectionService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;

@ExtendWith(MockitoExtension.class)
class CollectionQueryFacadeTest {

	@Mock
	private CollectionQueryService collectionQueryService;

	@Mock
	private CollectionQueryRepository collectionQueryRepository;

	@Mock
	private CollectionService collectionService;

	@Mock
	private CloudFrontUrlProvider cloudFrontUrlProvider;

	@InjectMocks
	private CollectionQueryFacade collectionQueryFacade;

	@ParameterizedTest
	@MethodSource("profileImageCases")
	@DisplayName("컬렉션 작성자의 프로필 이미지 key는 URL로 변환하고 외부 URL과 이미지 부재를 처리")
	void resolveAuthorProfileImage(String profileImage, String expectedUrl) {
		Long collectionId = 1L;
		Long userId = 10L;
		CollectionQueryRepository.GetCollectionHeader header = new CollectionQueryRepository.GetCollectionHeader(
			collectionId, "컬렉션 제목", "컬렉션 설명", null,
			LocalDateTime.of(2026, 1, 1, 0, 0),
			100L, "플린트", profileImage, "FLINER", false, true
		);
		when(collectionQueryRepository.getHeader(collectionId, userId)).thenReturn(header);
		when(collectionQueryRepository.getContentList(collectionId, userId)).thenReturn(List.of());
		CollectionQueryFacade facade = new CollectionQueryFacade(
			collectionQueryService, collectionQueryRepository, collectionService,
			new CloudFrontUrlProvider(new CloudFrontProperties("https://cdn.flint.kr", true))
		);

		GetCollectionDetailRes response = facade.getCollectionDetail(collectionId, userId);

		assertThat(response.author()).isEqualTo(
			new GetCollectionDetailRes.Author(100L, "플린트", expectedUrl, "FLINER")
		);
		assertThat(response.thumbnailUrl()).isNull();
		assertThat(response.contents()).isEmpty();
	}

	static Stream<Arguments> profileImageCases() {
		return Stream.of(
			Arguments.of("user/profile/profile.png", "https://cdn.flint.kr/user/profile/profile.png"),
			Arguments.of("https://example.com/profile.png", "https://example.com/profile.png"),
			Arguments.of("http://example.com/profile.png", "http://example.com/profile.png"),
			Arguments.of(null, null),
			Arguments.of("   ", null)
		);
	}

	@Nested
	@DisplayName("getCollectionDetail")
	class GetCollectionDetail {

		@Test
		@DisplayName("customImageUrls가 비어 있으면 resolver를 호출하지 않고 빈 목록으로 유지")
		void customImageUrlsEmpty() {
			// given
			Long collectionId = 1L;
			Long userId = 10L;
			CollectionQueryRepository.GetCollectionHeader header = new CollectionQueryRepository.GetCollectionHeader(
				collectionId,
				"컬렉션 제목",
				"컬렉션 설명",
				"collection.jpg",
				LocalDateTime.of(2026, 1, 1, 0, 0),
				100L,
				"플린트",
				"profile.jpg",
				"FLINER",
				false,
				true
			);
			GetCollectionDetailRes.Content content = new GetCollectionDetailRes.Content(
				200L,
				"콘텐츠 제목",
				"poster.jpg",
				List.of(),
				"감독",
				false,
				5,
				false,
				"추천 이유",
				2026
			);

			when(collectionQueryRepository.getHeader(collectionId, userId)).thenReturn(header);
			when(collectionQueryRepository.getContentList(collectionId, userId)).thenReturn(List.of(content));
			when(cloudFrontUrlProvider.resolveUrl(any()))
				.thenAnswer(invocation -> {
					String imageUrl = invocation.getArgument(0, String.class);
					if (imageUrl == null) {
						throw new NullPointerException("image key must not be null");
					}
					return "resolved/" + imageUrl;
				});

			// when
			GetCollectionDetailRes response = collectionQueryFacade.getCollectionDetail(collectionId, userId);

			// then
			GetCollectionDetailRes.Content resolvedContent = response.contents().getFirst();
			assertThat(response.thumbnailUrl()).isEqualTo("resolved/collection.jpg");
			assertThat(response.isPublic()).isTrue();
			assertThat(resolvedContent.imageUrl()).isEqualTo("resolved/poster.jpg");
			assertThat(resolvedContent.customImageUrls()).isEmpty();
			verify(cloudFrontUrlProvider, never()).resolveUrl(isNull());
		}

		@Test
		@DisplayName("customImageUrls는 요청 순서대로 전체 URL로 변환")
		void resolveCustomImageUrls() {
			// given
			Long collectionId = 1L;
			Long userId = 10L;
			CollectionQueryRepository.GetCollectionHeader header = new CollectionQueryRepository.GetCollectionHeader(
				collectionId,
				"컬렉션 제목",
				"컬렉션 설명",
				"collection.jpg",
				LocalDateTime.of(2026, 1, 1, 0, 0),
				100L,
				"플린트",
				"profile.jpg",
				"FLINER",
				false,
				true
			);
			GetCollectionDetailRes.Content content = new GetCollectionDetailRes.Content(
				200L,
				"콘텐츠 제목",
				"poster.jpg",
				List.of("custom-a.jpg", "custom-b.jpg"),
				"감독",
				false,
				5,
				false,
				"추천 이유",
				2026
			);

			when(collectionQueryRepository.getHeader(collectionId, userId)).thenReturn(header);
			when(collectionQueryRepository.getContentList(collectionId, userId)).thenReturn(List.of(content));
			when(cloudFrontUrlProvider.resolveUrl(any()))
				.thenAnswer(invocation -> "resolved/" + invocation.getArgument(0, String.class));

			// when
			GetCollectionDetailRes response = collectionQueryFacade.getCollectionDetail(collectionId, userId);

			// then
			assertThat(response.contents().getFirst().customImageUrls())
				.containsExactly("resolved/custom-a.jpg", "resolved/custom-b.jpg");
		}
	}
}
