#!/usr/bin/env bash
set -euo pipefail

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
abi=armeabi-v7a
debug_build=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        --abi)
            if [ "$#" -lt 2 ]; then
                printf '%s\n' 'Missing value for --abi' >&2
                exit 2
            fi
            abi=$2
            shift 2
            ;;
        --debug)
            debug_build=true
            shift
            ;;
        *)
            printf 'Unknown argument: %s\n' "$1" >&2
            exit 2
            ;;
    esac
done

case "$abi" in
    armeabi-v7a|armv7)
        abi=armeabi-v7a
        flavor=Armeabi_v7a
        artifact=armv7
        ;;
    arm64-v8a|arm64)
        abi=arm64-v8a
        flavor=Arm64_v8a
        artifact=arm64
        ;;
    *)
        printf 'Unsupported ABI: %s\n' "$abi" >&2
        exit 2
        ;;
esac

if [ "$debug_build" = true ]; then
    build_type=Debug
    suffix=-debug
else
    build_type=Release
    suffix=
fi
build_type_lower=$(printf '%s' "$build_type" | tr '[:upper:]' '[:lower:]')

python_bin=
if command -v python3.10 >/dev/null 2>&1; then
    python_bin=$(dirname "$(command -v python3.10)")
elif [ -x /opt/homebrew/opt/python@3.10/libexec/bin/python3.10 ]; then
    python_bin=/opt/homebrew/opt/python@3.10/libexec/bin
else
    printf '%s\n' 'BLOCKED: Python 3.10 is required by the pinned Chaquopy baseline.' >&2
    exit 2
fi

if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
    java_home=$JAVA_HOME
elif [ -x '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java' ] \
    && '/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java' -version 2>&1 | grep -q 'version "21'; then
    java_home='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
else
    printf '%s\n' 'BLOCKED: JDK 21 is required. Set JAVA_HOME or install Android Studio JBR 21.' >&2
    exit 2
fi

cd "$project_dir"
export TV_VERSION_CODE=${TV_VERSION_CODE:-10000}
export TV_VERSION_NAME=${TV_VERSION_NAME:-0.1.0}
python3 scripts/cinema-prepare.py
python3 scripts/cinema-verify-inputs.py

media_dir=$project_dir/$(
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["media"]["directory"])' \
        distribution/baseline.json
)
tv_dir=$project_dir/$(
    python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["tv"]["directory"])' \
        distribution/baseline.json
)
mkdir -p build/logs build/outputs

printf 'Building leanback %s %s version %s (%s) with MEDIA3_SOURCE_DIR=%s\n' \
    "$abi" "$build_type" "$TV_VERSION_NAME" "$TV_VERSION_CODE" "$media_dir"
(
    cd "$tv_dir"
    JAVA_HOME="$java_home" \
    MEDIA3_SOURCE_DIR="$media_dir" \
    PATH="$python_bin:$PATH" \
    ./gradlew \
        --init-script "$project_dir/distribution/sdk-compat.init.gradle" \
        ":app:assembleLeanback${flavor}${build_type}" \
        -PtvApplicationId=com.mjnhmd.tvcinema \
        --max-workers=2 \
        --console=plain
) 2>&1 | tee "$project_dir/build/logs/cinema-build.log"

apk=$(python3 - "$tv_dir" "$flavor" "$build_type_lower" "$TV_VERSION_CODE" "$TV_VERSION_NAME" <<'PY'
from pathlib import Path
import json
import sys

directory = Path(sys.argv[1]) / 'app/build/outputs/apk' / ('leanback' + sys.argv[2]) / sys.argv[3]
metadata = json.loads((directory / 'output-metadata.json').read_text())
assert metadata['applicationId'] == 'com.mjnhmd.tvcinema', 'Unexpected package metadata'
elements = metadata['elements']
assert len(elements) == 1, 'Expected exactly one ABI-specific APK'
element = elements[0]
assert element['versionCode'] == int(sys.argv[4]), 'Stale APK version code'
assert element['versionName'] == sys.argv[5], 'Stale APK version name'
name = element['outputFile']
assert Path(name).name == name, 'APK output escaped the variant directory'
output = directory / name
assert output.is_file(), 'Missing APK declared by Gradle metadata'
print(output)
PY
)
if [ -z "$apk" ]; then
    printf 'BLOCKED: no leanback %s %s APK found\n' "$abi" "$build_type" >&2
    exit 2
fi

output="$project_dir/build/outputs/tv-cinema-${artifact}${suffix}.apk"
cp "$apk" "$output"

sdk_dir=$(sed -n 's/^sdk.dir=//p' local.properties | tail -1)
if [ -z "$sdk_dir" ]; then
    printf '%s\n' 'BLOCKED: local.properties is missing sdk.dir' >&2
    exit 2
fi
build_tools=$(python3 -c 'import pathlib,re,sys; root=pathlib.Path(sys.argv[1])/"build-tools"; print(max((folder for folder in root.iterdir() if (folder/"aapt2").is_file()),key=lambda folder:tuple(int(part) for part in re.findall(r"\d+",folder.name))))' "$sdk_dir")

libs=$(unzip -Z1 "$output" | grep '^lib/' || true)
if [ -z "$libs" ]; then
    printf '%s\n' 'BLOCKED: APK has no native libraries' >&2
    exit 2
fi
if ! printf '%s\n' "$libs" | grep -q "^lib/${abi}/"; then
    printf 'BLOCKED: APK has no %s native libraries\n' "$abi" >&2
    exit 2
fi
unexpected=$(printf '%s\n' "$libs" | grep -v "^lib/${abi}/" || true)
if [ -n "$unexpected" ]; then
    printf 'BLOCKED: APK contains unexpected native paths:\n%s\n' "$unexpected" >&2
    exit 2
fi

badging=$("$build_tools/aapt2" dump badging "$output")
package_name=$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
min_sdk=$(printf '%s\n' "$badging" | sed -n "s/^minSdkVersion:'\([0-9]*\)'.*/\1/p; s/^sdkVersion:'\([0-9]*\)'.*/\1/p")
if [ "$package_name" != "com.mjnhmd.tvcinema" ]; then
    printf 'BLOCKED: unexpected package name %s\n' "$package_name" >&2
    exit 2
fi
if [ -z "$min_sdk" ] || [ "$min_sdk" -gt 28 ]; then
    printf 'BLOCKED: minSdk %s does not support the target Android 9 device\n' "$min_sdk" >&2
    exit 2
fi
if [ "$debug_build" != true ] && printf '%s\n' "$badging" | grep -q 'application-debuggable'; then
    printf '%s\n' 'BLOCKED: release APK is debuggable' >&2
    exit 2
fi

"$build_tools/apksigner" verify --print-certs "$output" >/dev/null

printf 'PASS: %s\n' "$output"
printf 'package=%s minSdk=%s abi=%s debug=%s\n' "$package_name" "$min_sdk" "$abi" "$debug_build"
printf 'Signed APK verified with native payload restricted to %s\n' "$abi"
