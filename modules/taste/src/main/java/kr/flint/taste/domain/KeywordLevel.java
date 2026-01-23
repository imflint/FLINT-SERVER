package kr.flint.taste.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(
    description = """
        취향 키워드 레벨 (5단계 분류)
        - LV1: 장르 (예: 액션, 로맨스, SF)
        - LV2: 분위기/감정 (예: 따뜻한, 긴장감 있는)
        - LV3: 서사/테마 (예: 성장, 복수, 우정)
        - LV4: 배경/문화권 (예: 한국, 미국, 판타지 세계)
        - LV5: 포맷 (예: 시리즈, 영화, 다큐멘터리)
        """,
    enumAsRef = true
)
@Getter
@RequiredArgsConstructor
public enum KeywordLevel {
    LV1("장르", KeywordColor.PINK),
    LV2("분위기/감정", KeywordColor.GREEN),
    LV3("서사/테마", KeywordColor.BLUE),
    LV4("배경/문화권", KeywordColor.YELLOW),
    LV5("포맷", KeywordColor.ORANGE);

    private final String description;
    private final KeywordColor color;
}
