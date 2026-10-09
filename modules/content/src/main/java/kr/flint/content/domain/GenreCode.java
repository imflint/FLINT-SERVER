package kr.flint.content.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

public enum GenreCode {
    ACTION("액션", "Action", "Action & Adventure", "action, action & adventure"),
    ADVENTURE("모험", "Adventure"),
    ANIMATION("애니메이션", "Animation"),
    COMEDY("코미디", "Comedy"),
    CRIME("범죄", "Crime"),
    DOCUMENTARY("다큐멘터리", "Documentary"),
    DRAMA("드라마", "Drama"),
    FAMILY("가족", "Family"),
    FANTASY("판타지", "Fantasy"),
    HISTORY("역사", "History"),
    HORROR("공포", "Horror", "호러"),
    MUSIC("음악", "Music"),
    MYSTERY("미스터리", "Mystery"),
    ROMANCE("로맨스", "Romance"),
    SCIENCE_FICTION("SF", "Science Fiction", "Sci-Fi", "Sci-Fi & Fantasy", "Science Fiction, Sci-fi & Fantasy"),
    TV_MOVIE("TV 영화", "TV Movie"),
    THRILLER("스릴러", "Thriller"),
    WAR("전쟁", "War", "War & Politics", "War, War & Politics"),
    WESTERN("서부", "Western"),
    KIDS("어린이", "Kids"),
    NEWS("뉴스", "News"),
    REALITY("리얼리티", "Reality"),
    SOAP("연속극", "Soap"),
    TALK("토크", "Talk");

    private final String displayName;
    private final String[] aliases;

    GenreCode(String displayName, String... aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<GenreCode> find(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String key = normalize(value);
        return Arrays.stream(values()).filter(code -> normalize(code.name()).equals(key)
            || normalize(code.displayName).equals(key)
            || Arrays.stream(code.aliases).anyMatch(alias -> normalize(alias).equals(key))).findFirst();
    }

    public static GenreCode resolve(String value) {
        return find(value).orElseThrow(() -> new GeneralException(ErrorCode.INVALID_INPUT, "등록되지 않은 장르입니다."));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
