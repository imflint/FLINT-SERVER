package kr.flint.taste.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KeywordLevel {
    L1("장르", KeywordColor.PINK),
    L2("분위기/감정", KeywordColor.GREEN),
    L3("서사/테마", KeywordColor.ORANGE),
    L4("배경/문화권", KeywordColor.YELLOW),
    L5("포맷", KeywordColor.BLUE);

    private final String description;
    private final KeywordColor color;
}
