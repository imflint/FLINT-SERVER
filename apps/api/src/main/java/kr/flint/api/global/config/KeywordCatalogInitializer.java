package kr.flint.api.global.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kr.flint.taste.domain.KeywordCatalog;
import kr.flint.taste.dto.KeywordCatalogSyncResult;
import kr.flint.taste.service.TasteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 부팅 시 {@code keywords} 테이블을 키워드 카탈로그에 맞춘다.
 *
 * <p>색상은 카탈로그에서 파생되지만 추천/검색 등 다른 조회는 여전히 DB level 을 읽으므로,
 * 양쪽이 어긋난 상태로 남지 않도록 여기서 한 번 보정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flint.taste.keyword-catalog-sync-enabled", matchIfMissing = true)
public class KeywordCatalogInitializer implements ApplicationRunner {

	private final TasteService tasteService;

	@Override
	public void run(ApplicationArguments args) {
		KeywordCatalogSyncResult result = tasteService.syncKeywordCatalog();
		if (result.hasChanges()) {
			log.info("키워드 카탈로그 동기화 완료. total={}, created={}, levelCorrected={}, unknown={}",
				KeywordCatalog.size(), result.created(), result.levelCorrected(), result.unknown());
			return;
		}
		log.debug("키워드 카탈로그 변경 없음. total={}, unknown={}", KeywordCatalog.size(), result.unknown());
	}
}
