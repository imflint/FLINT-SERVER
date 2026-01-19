package kr.flint.taste.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        취향 키워드 색상
        - PINK, GREEN, ORANGE, YELLOW, BLUE
        """,
    enumAsRef = true
)
public enum KeywordColor {
    PINK, GREEN, ORANGE, YELLOW, BLUE
}
