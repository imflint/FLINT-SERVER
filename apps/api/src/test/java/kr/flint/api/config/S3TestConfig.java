package kr.flint.api.config;

import kr.flint.infra.storage.s3.properties.S3Properties;
import kr.flint.shared.storage.FileExtension;
import kr.flint.shared.storage.StorageUploadUrl;
import kr.flint.shared.storage.StorageUrlProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class S3TestConfig {

    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_REGION = "ap-northeast-2";
    private static final Duration TEST_EXPIRATION = Duration.ofMinutes(10);

    @Bean
    @Primary
    public S3Properties s3Properties() {
        return new S3Properties(
                TEST_BUCKET,
                TEST_REGION,
                null,
                null,
                TEST_EXPIRATION
        );
    }

    @Bean
    @Primary
    public S3Client s3Client() {
        return mock(S3Client.class);
    }

    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        return mock(S3Presigner.class);
    }

    @Bean
    @Primary
    public StorageUrlProvider storageUrlProvider() {
        return new FakeStorageUrlProvider();
    }

    static class FakeStorageUrlProvider implements StorageUrlProvider {

        @Override
        public StorageUploadUrl generateUploadUrl(String key, FileExtension fileExtension) {
            String fakeUrl = String.format(
                    "https://%s.s3.%s.amazonaws.com/%s?X-Amz-Algorithm=AWS4-HMAC-SHA256",
                    TEST_BUCKET,
                    TEST_REGION,
                    key
            );
            return StorageUploadUrl.of(fakeUrl, key);
        }
    }
}
