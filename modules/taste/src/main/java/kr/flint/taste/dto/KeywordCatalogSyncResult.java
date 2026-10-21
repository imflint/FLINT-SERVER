package kr.flint.taste.dto;

/**
 * 키워드 카탈로그 동기화 결과.
 *
 * @param created        카탈로그에 있지만 DB에 없어 새로 추가한 키워드 수
 * @param levelCorrected DB level 이 카탈로그와 달라 보정한 키워드 수
 * @param unknown        DB 에만 있고 카탈로그에는 없는 키워드 수(삭제하지 않고 보고만 한다)
 */
public record KeywordCatalogSyncResult(
	int created,
	int levelCorrected,
	int unknown
) {
	public boolean hasChanges() {
		return created > 0 || levelCorrected > 0;
	}
}
