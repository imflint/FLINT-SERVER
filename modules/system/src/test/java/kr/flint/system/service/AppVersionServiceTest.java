package kr.flint.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.system.domain.AppPlatform;
import kr.flint.system.domain.AppVersion;
import kr.flint.system.exception.SystemErrorCode;
import kr.flint.system.exception.SystemException;
import kr.flint.system.repository.AppVersionRepository;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

	private static final String STORE_PATH = "https://apps.apple.com/kr/app/id000000000";

	@Mock
	private AppVersionRepository appVersionRepository;

	@InjectMocks
	private AppVersionService appVersionService;

	@Nested
	@DisplayName("getByPlatform")
	class GetByPlatform {

		@Test
		@DisplayName("등록된 플랫폼 정책을 조회")
		void returnsRegisteredPolicy() {
			AppVersion appVersion = AppVersion.create(AppPlatform.IOS, "1.2.3", STORE_PATH);
			when(appVersionRepository.findByPlatform(AppPlatform.IOS)).thenReturn(Optional.of(appVersion));

			assertThat(appVersionService.getByPlatform(AppPlatform.IOS)).isSameAs(appVersion);
		}

		@Test
		@DisplayName("정책이 없으면 APP_VERSION_NOT_FOUND — 앱은 이 실패를 통과 처리한다")
		void throwsWhenNotRegistered() {
			when(appVersionRepository.findByPlatform(AppPlatform.ANDROID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> appVersionService.getByPlatform(AppPlatform.ANDROID))
				.isInstanceOf(SystemException.class)
				.extracting("errorCode").isEqualTo(SystemErrorCode.APP_VERSION_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("upsert")
	class Upsert {

		@Test
		@DisplayName("정책이 없으면 새로 등록")
		void createsWhenAbsent() {
			when(appVersionRepository.findByPlatform(AppPlatform.IOS)).thenReturn(Optional.empty());
			when(appVersionRepository.save(any(AppVersion.class))).thenAnswer(call -> call.getArgument(0));

			AppVersion result = appVersionService.upsert(AppPlatform.IOS, "1.2.3", STORE_PATH);

			assertThat(result.getPlatform()).isEqualTo(AppPlatform.IOS);
			assertThat(result.getMinSupportedVersion()).isEqualTo("1.2.3");
			assertThat(result.getStorePath()).isEqualTo(STORE_PATH);
			verify(appVersionRepository).save(any(AppVersion.class));
		}

		@Test
		@DisplayName("정책이 있으면 기존 행을 변경하고 새로 저장하지 않는다")
		void updatesExistingRow() {
			AppVersion existing = AppVersion.create(AppPlatform.IOS, "1.0.0", "https://old");
			when(appVersionRepository.findByPlatform(AppPlatform.IOS)).thenReturn(Optional.of(existing));

			AppVersion result = appVersionService.upsert(AppPlatform.IOS, "2.0.0", STORE_PATH);

			assertThat(result).isSameAs(existing);
			assertThat(existing.getMinSupportedVersion()).isEqualTo("2.0.0");
			assertThat(existing.getStorePath()).isEqualTo(STORE_PATH);
			verify(appVersionRepository, never()).save(any(AppVersion.class));
		}

		@ParameterizedTest
		@DisplayName("형식이 깨진 버전은 저장하지 않는다 — 전체 사용자가 팝업에 갇히는 것을 막는다")
		@ValueSource(strings = {"1.2", "v1.2.3", "1.2.3.4", "latest", ""})
		void rejectsInvalidVersionFormat(String invalidVersion) {
			assertThatThrownBy(() -> appVersionService.upsert(AppPlatform.IOS, invalidVersion, STORE_PATH))
				.isInstanceOf(SystemException.class)
				.extracting("errorCode").isEqualTo(SystemErrorCode.INVALID_APP_VERSION_FORMAT);

			verify(appVersionRepository, never()).findByPlatform(any());
			verify(appVersionRepository, never()).save(any(AppVersion.class));
		}

		@Test
		@DisplayName("스토어 주소가 비면 저장하지 않는다")
		void rejectsBlankStorePath() {
			assertThatThrownBy(() -> appVersionService.upsert(AppPlatform.IOS, "1.2.3", "  "))
				.isInstanceOf(SystemException.class)
				.extracting("errorCode").isEqualTo(SystemErrorCode.INVALID_STORE_PATH);

			verify(appVersionRepository, never()).save(any(AppVersion.class));
		}
	}

	@Nested
	@DisplayName("requiresUpdate")
	class RequiresUpdate {

		@Test
		@DisplayName("최소 버전 미만이면 true, 같거나 높으면 false")
		void decidesByMinSupportedVersion() {
			AppVersion appVersion = AppVersion.create(AppPlatform.IOS, "1.2.3", STORE_PATH);

			assertThat(appVersion.requiresUpdate("1.2.2")).isTrue();
			assertThat(appVersion.requiresUpdate("1.2.3")).isFalse();
			assertThat(appVersion.requiresUpdate("1.10.0")).isFalse();
		}

		@Test
		@DisplayName("앱이 보낸 버전 형식이 깨졌으면 강제하지 않는다")
		void doesNotForceOnUnparsableClientVersion() {
			AppVersion appVersion = AppVersion.create(AppPlatform.IOS, "1.2.3", STORE_PATH);

			assertThat(appVersion.requiresUpdate("unknown")).isFalse();
			assertThat(appVersion.requiresUpdate(null)).isFalse();
		}
	}
}
