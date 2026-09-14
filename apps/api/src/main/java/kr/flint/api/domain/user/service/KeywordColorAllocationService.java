package kr.flint.api.domain.user.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import kr.flint.taste.domain.KeywordColor;
import kr.flint.taste.dto.response.UserKeywordProjection;

@Service
public class KeywordColorAllocationService {

    private static final List<KeywordColor> FALLBACK_ORDER = List.of(
        KeywordColor.PINK,
        KeywordColor.GREEN,
        KeywordColor.ORANGE,
        KeywordColor.YELLOW,
        KeywordColor.BLUE
    );

    public List<KeywordColor> allocate(List<UserKeywordProjection> keywords) {
        int size = Math.min(keywords.size(), 6);
        List<KeywordColor> result = new ArrayList<>(size);
        Set<KeywordColor> usedTopColors = EnumSet.noneOf(KeywordColor.class);

        for (int index = 0; index < size; index++) {
            KeywordColor preferred = keywords.get(index).getLevel().getColor();
            if (index >= 3) {
                result.add(preferred);
                continue;
            }

            KeywordColor selected = usedTopColors.contains(preferred)
                ? firstUnused(usedTopColors)
                : preferred;
            usedTopColors.add(selected);
            result.add(selected);
        }
        return List.copyOf(result);
    }

    private KeywordColor firstUnused(Set<KeywordColor> usedColors) {
        return FALLBACK_ORDER.stream()
            .filter(color -> !usedColors.contains(color))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No keyword color is available"));
    }
}
