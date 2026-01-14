package kr.flint.taste.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KeywordLevel {
    LV1("장르", KeywordColor.PINK),
    LV2("분위기/감정", KeywordColor.GREEN),
    LV3("서사/테마", KeywordColor.ORANGE),
    LV4("배경/문화권", KeywordColor.YELLOW),
    LV5("포맷", KeywordColor.BLUE);

    private final String description;
    private final KeywordColor color;
}
