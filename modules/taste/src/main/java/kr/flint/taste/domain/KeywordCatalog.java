package kr.flint.taste.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 취향 키워드 정본(canonical source).
 *
 * <p>키워드 이름 → 레벨 → 색상 매핑의 유일한 출처다. GPT 응답의 level/category 값이나
 * {@code keywords} 테이블의 level 컬럼이 아니라 이 카탈로그가 색상을 결정하므로,
 * 프롬프트가 흔들리거나 DB 데이터가 어긋나도 색상 매핑은 변하지 않는다.
 */
public final class KeywordCatalog {

    /**
     * GPT가 자주 흘리는 표기 변형 → 정식 이름.
     *
     * <p>공백/구두점/대소문자는 {@link #toLookupKey}가 이미 흡수하므로, 여기에는 어간이
     * 실제로 다른 변형(번역어, 동의어, 접미사 차이)만 넣는다.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        // LV1 장르 - 동의어
        Map.entry("공포", "호러"),
        Map.entry("애니", "애니메이션"),
        Map.entry("다큐", "다큐멘터리"),
        Map.entry("아동", "어린이"),
        Map.entry("키즈", "어린이"),
        Map.entry("공상과학", "SF"),
        Map.entry("사이언스픽션", "SF"),
        Map.entry("에스에프", "SF"),
        Map.entry("시대극", "사극"),
        // LV1 장르 - 영문
        Map.entry("adventure", "모험"),
        Map.entry("comedy", "코미디"),
        Map.entry("documentary", "다큐멘터리"),
        Map.entry("drama", "드라마"),
        Map.entry("family", "가족"),
        Map.entry("history", "역사"),
        Map.entry("action", "액션"),
        Map.entry("crime", "범죄"),
        Map.entry("horror", "호러"),
        Map.entry("mystery", "미스터리"),
        Map.entry("sciencefiction", "SF"),
        Map.entry("thriller", "스릴러"),
        Map.entry("war", "전쟁"),
        Map.entry("animation", "애니메이션"),
        Map.entry("fantasy", "판타지"),
        Map.entry("music", "음악"),
        Map.entry("romance", "로맨스"),
        Map.entry("kids", "어린이"),
        // LV2 분위기/감정 - 접미사 변형
        Map.entry("슬픔", "슬픈"),
        Map.entry("불안", "불안한"),
        Map.entry("어둠", "어두운"),
        Map.entry("다크", "어두운"),
        Map.entry("긴장", "긴장되는"),
        Map.entry("긴장감", "긴장되는"),
        Map.entry("긴장감 있는", "긴장되는"),
        Map.entry("잔혹", "잔혹한"),
        Map.entry("잔혹함", "잔혹한"),
        Map.entry("잔잔", "잔잔한"),
        Map.entry("힐링", "잔잔한"),
        Map.entry("따뜻한", "잔잔한"),
        Map.entry("설렘", "설레는"),
        Map.entry("설레임", "설레는"),
        Map.entry("몽환", "몽환적인"),
        Map.entry("몽환적", "몽환적인"),
        Map.entry("유쾌", "유쾌한"),
        Map.entry("경쾌한", "유쾌한"),
        // LV3 서사/테마
        Map.entry("노년", "노년의 삶"),
        Map.entry("실화", "실화 기반"),
        Map.entry("실화바탕", "실화 기반"),
        Map.entry("자아", "정체성"),
        Map.entry("정치", "권력"),
        Map.entry("직장", "오피스"),
        Map.entry("신앙", "종교"),
        Map.entry("리벤지", "복수"),
        Map.entry("성장물", "성장"),
        Map.entry("성장서사", "성장"),
        Map.entry("청춘물", "청춘"),
        Map.entry("서바이벌", "생존"),
        // LV4 배경/시대
        Map.entry("포스트아포칼립스", "아포칼립스"),
        Map.entry("dystopia", "디스토피아"),
        // LV5 문화권
        Map.entry("미국", "북미"),
        Map.entry("미주", "북미"),
        Map.entry("usa", "북미"),
        Map.entry("america", "북미"),
        Map.entry("northamerica", "북미"),
        Map.entry("korea", "한국"),
        Map.entry("japan", "일본"),
        Map.entry("europe", "유럽"),
        Map.entry("india", "인도"),
        Map.entry("uk", "영국"),
        Map.entry("britain", "영국")
    );

    private static final Map<KeywordLevel, List<String>> NAMES_BY_LEVEL = buildNamesByLevel();
    private static final List<Entry> ENTRIES = buildEntries();
    private static final Map<String, Entry> ENTRY_BY_LOOKUP_KEY = buildLookupIndex();

    private KeywordCatalog() {
    }

    public record Entry(String name, KeywordLevel level) {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Map<KeywordLevel, List<String>> namesByLevel() {
        return NAMES_BY_LEVEL;
    }

    public static List<String> namesOf(KeywordLevel level) {
        return NAMES_BY_LEVEL.getOrDefault(level, List.of());
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** 표기가 흔들린 이름(공백/대소문자/별칭/영문)을 카탈로그의 정식 이름으로 되돌린다. */
    public static Optional<String> canonicalize(String rawName) {
        return findEntry(rawName).map(Entry::name);
    }

    public static Optional<KeywordLevel> findLevel(String rawName) {
        return findEntry(rawName).map(Entry::level);
    }

    /** 카탈로그에 없는 이름이면 fallback 레벨을 쓴다. fallback 까지 없으면 LV1. */
    public static KeywordLevel levelOf(String rawName, KeywordLevel fallback) {
        return findLevel(rawName).orElseGet(() -> fallback != null ? fallback : KeywordLevel.LV1);
    }

    public static KeywordColor colorOf(String rawName, KeywordLevel fallback) {
        return levelOf(rawName, fallback).getColor();
    }

    public static Optional<Entry> findEntry(String rawName) {
        String lookupKey = toLookupKey(rawName);
        if (lookupKey.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ENTRY_BY_LOOKUP_KEY.get(lookupKey));
    }

    // 공백/구두점/대소문자/유니코드 정규화 형태 차이를 흡수해 비교 키를 만든다
    static String toLookupKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(Character.toLowerCase(codePoint));
            }
        });
        return builder.toString();
    }

    private static Map<KeywordLevel, List<String>> buildNamesByLevel() {
        Map<KeywordLevel, List<String>> namesByLevel = new LinkedHashMap<>();
        namesByLevel.put(KeywordLevel.LV1, List.of(
            "모험", "코미디", "다큐멘터리", "드라마", "가족", "역사", "액션", "범죄", "호러",
            "미스터리", "SF", "스릴러", "전쟁", "애니메이션", "판타지", "음악", "로맨스", "어린이"
        ));
        namesByLevel.put(KeywordLevel.LV2, List.of(
            "슬픈", "불안한", "어두운", "긴장되는", "잔혹한", "잔잔한", "설레는", "몽환적인", "유쾌한"
        ));
        namesByLevel.put(KeywordLevel.LV3, List.of(
            "노년의 삶", "실화 기반", "사회문제", "정체성", "권력", "오피스", "종교",
            "복수", "추리", "성장", "브로맨스", "우정", "청춘", "생존"
        ));
        namesByLevel.put(KeywordLevel.LV4, List.of(
            "사극", "디스토피아", "근미래", "근대", "아포칼립스"
        ));
        namesByLevel.put(KeywordLevel.LV5, List.of(
            "한국", "북미", "유럽", "인도", "일본", "영국"
        ));
        return Collections.unmodifiableMap(namesByLevel);
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        NAMES_BY_LEVEL.forEach((level, names) -> names.forEach(name -> entries.add(new Entry(name, level))));
        return List.copyOf(entries);
    }

    private static Map<String, Entry> buildLookupIndex() {
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            index.put(toLookupKey(entry.name()), entry);
        }
        ALIASES.forEach((alias, canonicalName) -> {
            Entry entry = index.get(toLookupKey(canonicalName));
            if (entry == null) {
                throw new IllegalStateException("별칭이 카탈로그에 없는 키워드를 가리킨다: " + alias + " -> " + canonicalName);
            }
            index.putIfAbsent(toLookupKey(alias), entry);
        });
        return Collections.unmodifiableMap(index);
    }
}
