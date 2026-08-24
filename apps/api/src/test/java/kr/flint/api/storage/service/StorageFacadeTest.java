package kr.flint.api.storage.service;

import kr.flint.api.global.storage.exception.StorageErrorCode;
import kr.flint.api.global.storage.exception.StorageException;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.api.global.storage.service.StorageUploadTarget;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageFacadeTest {

	@Mock
	private StorageUrlProvider storageUrlProvider;

	@InjectMocks
    private StorageFacade storageFacade;

    @Nested
    @DisplayName("getUploadUrl")
    class GetUploadUrl {

        @Test
        @DisplayName("정상적인 pathType과 extension으로 URL 발급")
        void success() {
            mockUploadUrl();

            // when
            StorageUploadUrl response = storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, FileExtension.JPG);

            // then
            assertThat(response.uploadUrl()).isNotBlank();
            assertThat(response.key()).startsWith("user/profile/");
            assertThat(response.key()).endsWith(".jpg");
        }

        @Test
        @DisplayName("허용되지 않은 확장자 - INVALID_FILE_EXTENSION")
        void invalidExtension() {
            // when & then (이미지 저장 경로는 PDF를 허용하지 않음)
            assertThatThrownBy(() -> storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, FileExtension.PDF))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }

		@Test
		@DisplayName("모든 이미지 저장 경로는 WebP 업로드 URL 발급을 지원")
		void allImagePathsSupportWebp() {
			mockUploadUrl();

			List<StorageUploadUrl> responses = storageFacade.getUploadUrls(List.of(
				StorageUploadTarget.of(StoragePathType.USER_PROFILE, FileExtension.WEBP),
				StorageUploadTarget.of(StoragePathType.LOGO_IMAGE, FileExtension.WEBP),
				StorageUploadTarget.of(StoragePathType.COLLECTION_THUMBNAIL, FileExtension.WEBP),
				StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.WEBP)
			));

			assertThat(responses)
				.extracting(StorageUploadUrl::key)
				.allSatisfy(key -> assertThat(key).endsWith(".webp"));
			assertThat(responses.get(0).key()).startsWith("user/profile/");
			assertThat(responses.get(1).key()).startsWith("keywords/logo/");
			assertThat(responses.get(2).key()).startsWith("collection/thumbnail/");
			assertThat(responses.get(3).key()).startsWith("collection/content/");
		}
    }

    @Nested
    @DisplayName("getUploadUrls")
    class GetUploadUrls {

        @Test
        @DisplayName("각 item의 pathType과 extension으로 URL 발급")
        void success() {
            mockUploadUrl();

            // when
            List<StorageUploadUrl> responses = storageFacade.getUploadUrls(List.of(
                    StorageUploadTarget.of(StoragePathType.COLLECTION_THUMBNAIL, FileExtension.PNG),
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.JPG),
                    StorageUploadTarget.of(StoragePathType.USER_PROFILE, FileExtension.JPEG)
            ));

            // then
            assertThat(responses).hasSize(3);
            assertThat(responses)
                    .extracting(StorageUploadUrl::key)
                    .doesNotHaveDuplicates();
            assertThat(responses.get(0).key()).startsWith("collection/thumbnail/");
            assertThat(responses.get(0).key()).endsWith(".png");
            assertThat(responses.get(1).key()).startsWith("collection/content/");
            assertThat(responses.get(1).key()).endsWith(".jpg");
            assertThat(responses.get(2).key()).startsWith("user/profile/");
            assertThat(responses.get(2).key()).endsWith(".jpeg");
            assertThat(responses)
                    .extracting(StorageUploadUrl::uploadUrl)
                    .allSatisfy(uploadUrl -> assertThat(uploadUrl).isNotBlank());
        }

        @Test
        @DisplayName("허용되지 않은 확장자 - INVALID_FILE_EXTENSION")
        void invalidExtension() {
            // when & then (이미지 저장 경로는 PDF를 허용하지 않음)
            assertThatThrownBy(() -> storageFacade.getUploadUrls(List.of(
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.JPG),
                    StorageUploadTarget.of(StoragePathType.COLLECTION_CONTENT, FileExtension.PDF)
            )))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }

	private void mockUploadUrl() {
		when(storageUrlProvider.generateUploadUrl(anyString(), any(FileExtension.class)))
			.thenAnswer(invocation -> StorageUploadUrl.of(
				"https://upload.example.com",
				invocation.getArgument(0, String.class)
			));
	}
}
