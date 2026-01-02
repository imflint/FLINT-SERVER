package kr.flint.shared.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class QueryDslUtil {

    public static Predicate emptyCondition() {
        return new BooleanBuilder();
    }

    /**
     * 조건이 true일 때만 Predicate 적용
     */
    public static Predicate onCondition(boolean condition, Supplier<Predicate> onTrueSupplier) {
        return condition ? onTrueSupplier.get() : emptyCondition();
    }

    /**
     * 조건에 따라 다른 Predicate 적용
     */
    public static Predicate onCondition(
            boolean condition,
            Supplier<Predicate> onTrueSupplier,
            Supplier<Predicate> onFalseSupplier
    ) {
        return condition ? onTrueSupplier.get() : onFalseSupplier.get();
    }

    /**
     * 값이 null이 아닐 때만 Predicate 적용
     */
    public static <T> Predicate onCondition(
            @Nullable T value,
            Function<T, Predicate> predicateFunction
    ) {
        return value == null ? emptyCondition() : predicateFunction.apply(value);
    }

    /**
     * 값이 null인지 여부에 따라 다른 Predicate 적용
     */
    public static <T> Predicate onCondition(
            @Nullable T value,
            Function<T, Predicate> onNotNullFunction,
            Supplier<Predicate> onNullSupplier
    ) {
        return value == null ? onNullSupplier.get() : onNotNullFunction.apply(value);
    }

    /**
     * 컬렉션이 비어있지 않을 때만 Predicate 적용
     */
    public static <T> Predicate onNotEmpty(
            @Nullable Collection<T> collection,
            Function<Collection<T>, Predicate> predicateFunction
    ) {
        return collection == null || collection.isEmpty()
                ? emptyCondition()
                : predicateFunction.apply(collection);
    }
}
