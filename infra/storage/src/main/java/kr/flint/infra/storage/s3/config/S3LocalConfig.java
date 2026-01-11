package kr.flint.infra.storage.s3.config;

import kr.flint.infra.storage.s3.properties.S3Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("local")
@EnableConfigurationProperties(S3Properties.class)
public class S3LocalConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()));

        if (properties.hasCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            properties.accessKey(),
                            properties.secretKey()
                    )
            ));
        }

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()));

        if (properties.hasCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            properties.accessKey(),
                            properties.secretKey()
                    )
            ));
        }

        return builder.build();
    }
}