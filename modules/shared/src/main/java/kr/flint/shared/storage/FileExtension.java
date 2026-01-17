package kr.flint.shared.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "허용 파일 확장자", enumAsRef = true)
@Getter
@RequiredArgsConstructor
public enum FileExtension {

    @Schema(description = "JPG 이미지 (image/jpeg)")
    JPG("jpg", "image/jpeg"),

    @Schema(description = "JPEG 이미지 (image/jpeg)")
    JPEG("jpeg", "image/jpeg"),

    @Schema(description = "PNG 이미지 (image/png)")
    PNG("png", "image/png"),

    @Schema(description = "GIF 이미지 (image/gif)")
    GIF("gif", "image/gif"),

    @Schema(description = "WebP 이미지 (image/webp)")
    WEBP("webp", "image/webp"),

    @Schema(description = "SVG 벡터 이미지 (image/svg+xml)")
    SVG("svg", "image/svg+xml"),

    @Schema(description = "PDF 문서 (application/pdf)")
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;
}
