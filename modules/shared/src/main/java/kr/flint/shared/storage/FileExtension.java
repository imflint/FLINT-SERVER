package kr.flint.shared.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileExtension {

    JPG("jpg", "image/jpeg"),
    JPEG("jpeg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp"),
    SVG("svg", "image/svg+xml"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;
}
