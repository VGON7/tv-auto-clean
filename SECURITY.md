# Security

## Reporting a vulnerability

Please open a GitHub Issue with the `security` label, or contact the maintainer privately if the issue is sensitive. Do not publish working exploit code before a fix is available.

## Signing key

The release APK is signed with the maintainer's private key. The keystore is **not** included in this repository and must never be committed.

If you build the app yourself, use your own keystore. Do not reuse the maintainer's key.

## Accessibility service

The app requests an Android AccessibilityService with key-event filtering. It does **not** retrieve window content (`canRetrieveWindowContent=false`) and does not have the `INTERNET` permission. The service is used only to match remote-control key codes to user-configured actions.

Android displays a generic warning for all accessibility services. Review the source before enabling it.
