package kr.flint.api.storage.service;

import kr.flint.api.config.AppleOAuthTestConfig;
import kr.flint.api.config.GptTestConfig;
import kr.flint.api.config.RedisTestConfig;
import kr.flint.api.config.S3TestConfig;
import kr.flint.api.global.storage.exception.StorageErrorCode;
import kr.flint.api.global.storage.exception.StorageException;
import kr.flint.api.global.storage.service.StorageFacade;
import kr.flint.infra.storage.enums.StoragePathType;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@Import({S3TestConfig.class, RedisTestConfig.class, GptTestConfig.class, AppleOAuthTestConfig.class})
class StorageFacadeTest {

    @Autowired
    private StorageFacade storageFacade;

    @Nested
    @DisplayName("getUploadUrl")
    class GetUploadUrl {

        @Test
        @DisplayName("정상적인 pathType과 extension으로 URL 발급")
        void success() {
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
            // when & then (USER_PROFILE은 JPG, JPEG, PNG만 허용)
            assertThatThrownBy(() -> storageFacade.getUploadUrl(StoragePathType.USER_PROFILE, FileExtension.PDF))
                    .isInstanceOf(StorageException.class)
                    .extracting("errorCode")
                    .isEqualTo(StorageErrorCode.INVALID_FILE_EXTENSION);
        }
    }
}
