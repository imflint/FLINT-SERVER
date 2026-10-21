package kr.flint.infra.gpt.dto;

import java.util.List;

/**
 * GPT 프롬프트에 넣을 선택 가능 키워드 목록. 호출하는 쪽(apps/api)이 키워드 카탈로그에서
 * 만들어 넘기므로, 프롬프트의 후보 목록이 서버 카탈로그와 어긋날 수 없다.
 *
 * @param level    레벨 식별자 (예: LV1)
 * @param label    레벨 설명 (예: 장르)
 * @param keywords 해당 레벨의 선택 가능 키워드 (정식 표기)
 */
public record TasteKeywordLevelDto(
	String level,
	String label,
	List<String> keywords
) {
}
