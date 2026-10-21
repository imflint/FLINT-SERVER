package kr.flint.taste.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(
    description = """
        취향 키워드 레벨 (5단계 분류)
        - LV1: 장르 (예: 액션, 로맨스, SF) - PINK
        - LV2: 분위기/감정 (예: 잔잔한, 긴장되는) - GREEN
        - LV3: 서사/테마 (예: 성장, 복수, 우정) - BLUE
        - LV4: 배경/시대 (예: 사극, 디스토피아, 근미래) - YELLOW
        - LV5: 문화권 (예: 한국, 일본, 북미) - ORANGE
        """,
    enumAsRef = true
)
@Getter
@RequiredArgsConstructor
public enum KeywordLevel {
    LV1("장르", KeywordColor.PINK, "keywords/logo/default-lv1.png"),
    LV2("분위기/감정", KeywordColor.GREEN, "keywords/logo/default-lv2.png"),
    LV3("서사/테마", KeywordColor.BLUE, "keywords/logo/default-lv3.png"),
    LV4("배경/시대", KeywordColor.YELLOW, "keywords/logo/default-lv4.png"),
    LV5("문화권", KeywordColor.ORANGE, "keywords/logo/default-lv5.png");

    private final String description;
    private final KeywordColor color;
    /** 키워드별 아이콘이 등록되지 않았을 때 내려줄 레벨 기본 아이콘의 S3 키 */
    private final String defaultIconKey;
}
