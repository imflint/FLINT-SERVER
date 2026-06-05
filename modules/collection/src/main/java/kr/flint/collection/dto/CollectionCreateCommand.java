package kr.flint.collection.dto;

import java.util.List;

public record CollectionCreateCommand(
        String title,
        String description,
        String imageUrl,
        boolean isPublic,
        List<ContentInput> contents
) {

    public static CollectionCreateCommand of(
            String title,
            String description,
            String imageUrl,
            boolean isPublic,
            List<ContentInput> contents
    ) {
        return new CollectionCreateCommand(title, description, imageUrl, isPublic, contents);
    }

    public record ContentInput(
            Long contentId,
            boolean isSpoiler,
            String reason,
            List<String> customImages
    ) {
        public ContentInput {
            customImages = customImages == null ? List.of() : List.copyOf(customImages);
        }

        public static ContentInput of(Long contentId, boolean isSpoiler, String reason, List<String> customImages) {
            return new ContentInput(contentId, isSpoiler, reason, customImages);
        }
    }
}
