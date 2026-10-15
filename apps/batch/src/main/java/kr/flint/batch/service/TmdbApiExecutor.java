package kr.flint.batch.service;

import java.util.function.Supplier;

import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import feign.FeignException;
import feign.codec.DecodeException;
import kr.flint.batch.config.BatchProperties;
import kr.flint.batch.config.TmdbRetryPolicyFactory;

@Component
public class TmdbApiExecutor {

    private final RetryTemplate retryTemplate;

    public TmdbApiExecutor(BatchProperties properties, TmdbRetryPolicyFactory policyFactory) {
        int attempts = properties.tmdb().retryAttempts();
        if (attempts < 1) throw new IllegalArgumentException("TMDB retry attempts must be positive");
        var retry = new SimpleRetryPolicy(attempts);
        var noRetry = new NeverRetryPolicy();
        var policy = new ExceptionClassifierRetryPolicy();
        policy.setExceptionClassifier(error -> error instanceof TmdbApiException failure && failure.retryable()
            ? retry : noRetry);
        retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(policy);
        retryTemplate.setBackOffPolicy(policyFactory.fixedBackOffPolicy());
    }

    public <T> T execute(String operation, Supplier<T> call) {
        return retryTemplate.execute(context -> {
            try {
                return call.get();
            } catch (TmdbApiException failure) {
                throw failure;
            } catch (DecodeException failure) {
                throw TmdbApiException.invalidResponse(operation);
            } catch (FeignException failure) {
                throw TmdbApiException.httpFailure(operation, failure.status());
            } catch (RuntimeException failure) {
                // 원본 예외 원인은 URL, 응답 본문 또는 인증 정보를 포함할 수 있다.
                throw TmdbApiException.permanent(operation);
            }
        });
    }
}
