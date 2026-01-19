package kr.flint.api.domain.collection.util;

import java.util.List;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectionImageProcessor {
	private static final int MAX_IMAGE_COUNT = 2;

	public static List<String> limitAndResolveImages(List<String> posters, Function<String, String> urlResolver) {
		return posters.stream()
			.limit(MAX_IMAGE_COUNT)
			.map(urlResolver)
			.toList();
	}

	public static String selectThumbnail(List<String> resolvedImages) {
		return resolvedImages.isEmpty() ? null : resolvedImages.getFirst();
	}
}
