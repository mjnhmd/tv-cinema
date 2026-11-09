#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
python3 scripts/cinema-verify-inputs.py
if [ ! -f local.properties ]; then
    printf '%s\n' 'Missing local.properties: configure sdk.dir and your private release signing values.'
    exit 2
fi
sh ./gradlew -PcinemaHostOnly=true :app:assembleLeanbackRelease --console=plain "$@"
printf '%s\n' 'TV ARMv7 output: app/build/outputs/apk/leanback/release/leanback-armeabi_v7a.apk'
printf '%s\n' 'Inspect the final manifest, signing certificate and ARMv7 native payload before delivery.'
