# APK and AAB Size Limits

This document outlines the acceptable size limits for the MyPlanet Android application builds. These limits are enforced by the CI/CD pipeline.

## Limits Configuration

The limits are defined in `scripts/size_limits.json`.

| Build Flavor | Build Type | Artifact | Max Size (MB) | Max Size (Bytes) |
|---|---|---|---|---|
| Default | Debug | APK | 100 MB | 104,857,600 |
| Lite | Debug | APK | 100 MB | 104,857,600 |
| Default | Release | APK | 60 MB | 62,914,560 |
| Lite | Release | APK | 60 MB | 62,914,560 |
| Default | Release | AAB | 150 MB | 157,286,400 |
| Lite | Release | AAB | 150 MB | 157,286,400 |

## Thresholds

- **Warning**: If the build size exceeds the limit, the CI pipeline will flag it as a warning in the size report.
- **Trend**: The pipeline tracks size over time to detect unexpected increases.

## Managing Size

If the application size grows significantly:
1.  **Analyze Dependencies**: Check if new libraries were added. Use ProGuard/R8 to shrink code.
2.  **Inspect Resources**: Check for large images or media files. Convert PNGs to WebP.
3.  **Optimize Native Libraries**: Ensure only necessary architectures are included.

## Updating Limits

To update the limits, edit `scripts/size_limits.json` and update this document to match.
