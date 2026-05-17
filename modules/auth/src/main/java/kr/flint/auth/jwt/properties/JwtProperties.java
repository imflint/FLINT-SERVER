package kr.flint.auth.jwt.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(
        @NotBlank
        @Size(min = 32)
        String secret,
        @NotNull
        Duration accessExpiration,
        @NotNull
        Duration refreshExpiration,
        @NotNull
        Duration tempExpiration
) {
    public JwtProperties {
        if (secret != null) {
            secret = secret.trim();
        }
    }
}
