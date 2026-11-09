#!/usr/bin/env python3
import io
import sys
import zipfile
from pathlib import Path


REQUIRED = (
    "androidx/media3/common/DecoderMode.class",
    "androidx/media3/common/DiscMenuController.class",
    "androidx/media3/common/DiscMenuState.class",
    "androidx/media3/common/text/SubtitleContent.class",
    "androidx/media3/common/text/SubtitleOffsets.class",
    "androidx/media3/exoplayer/libass/LibassConfiguration.class",
    "androidx/media3/exoplayer/libass/LibassPlaybackSession.class",
    "androidx/media3/exoplayer/libass/LibassSubtitleController.class",
    "androidx/media3/exoplayer/iso/IsoDiscPreparation.class",
    "androidx/media3/exoplayer/text/SecondaryTextOutput.class",
    "androidx/media3/exoplayer/trackselection/SecondaryTextTrackSelector.class",
    "androidx/media3/effect/VideoAdjustment.class",
    "androidx/media3/ui/danmaku/DanmakuPlayerViewController.class",
)


def main():
    project = Path(__file__).resolve().parent.parent
    archives = sorted((project / "app/libs").glob("lib-*.aar"))
    classes = set()
    failures = []
    for archive_path in archives:
        try:
            with zipfile.ZipFile(archive_path) as archive:
                with zipfile.ZipFile(io.BytesIO(archive.read("classes.jar"))) as jar:
                    classes.update(jar.namelist())
        except (OSError, KeyError, zipfile.BadZipFile) as error:
            failures.append(f"{archive_path.name}: {type(error).__name__}")
    missing = [class_name for class_name in REQUIRED if class_name not in classes]
    if failures or missing:
        print("BLOCKED: the supplied Media3 AARs do not match FongMi TV 5.8.7.")
        for failure in failures:
            print(f"  Invalid AAR: {failure}")
        for class_name in missing:
            print(f"  Missing: {class_name}")
        print("Provide matching upstream lib-*.aar files in app/libs; keep them out of Git.")
        print("Do not bypass native verification or substitute an upstream APK.")
        return 2
    print(f"PASS: required Media3 class entries found in {len(archives)} AARs.")
    print("Gradle compilation and native verification must still pass.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
