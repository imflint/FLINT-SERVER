package kr.flint.shared.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(
    description = """
        허용 파일 확장자
        - JPG: JPG 이미지 (image/jpeg)
        - JPEG: JPEG 이미지 (image/jpeg)
        - PNG: PNG 이미지 (image/png)
        - GIF: GIF 이미지 (image/gif)
        - WEBP: WebP 이미지 (image/webp)
        - SVG: SVG 벡터 이미지 (image/svg+xml)
        - PDF: PDF 문서 (application/pdf)
        """,
    enumAsRef = true
)
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
