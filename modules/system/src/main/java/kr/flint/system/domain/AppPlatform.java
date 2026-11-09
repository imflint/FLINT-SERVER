package kr.flint.system.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = """
        앱 플랫폼
        - IOS: iOS 앱 (App Store)
        - ANDROID: Android 앱 (Google Play)
        """,
    enumAsRef = true
)
public enum AppPlatform {
    IOS,
    ANDROID
}
