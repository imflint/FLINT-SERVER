package kr.flint.shared.util;

import kr.flint.shared.domain.HasId;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class ExtractUtil {

    public static List<Long> extractIdList(Collection<? extends HasId> items) {
        return extractList(items, HasId::getId);
    }

    public static Set<Long> extractIdSet(Collection<? extends HasId> items) {
        return extractSet(items, HasId::getId);
    }

    public static <T, R> List<R> extractList(Collection<T> items, Function<T, R> mapper) {
        return items.stream()
                .map(mapper)
                .toList();
    }

    public static <T, R> Set<R> extractSet(Collection<T> items, Function<T, R> mapper) {
        return items.stream()
                .map(mapper)
                .collect(Collectors.toSet());
    }
}
