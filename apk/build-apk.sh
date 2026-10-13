#!/usr/bin/env bash
# Patch the released QuestCraft APK with our Pojlib changes and our Vivecraft jar, then sign it.
#
#   apk/build-apk.sh --apk questcraft.apk --jar vivecraft-...-fabric.jar --variant replace|side --out DIR [--tag mihail.N]
#
# We cannot rebuild QuestCraft from source (the Unity launcher needs the Unity Editor), so we take the
# released APK and replace only the launcher classes listed in apk/patched-sources.txt.
#
# Variants:
#   replace  same package (com.qcxr.qcxr). Installing it over the official app requires uninstalling
#            that first (different signature), which deletes /sdcard/Android/data/com.qcxr.qcxr.
#   side     renamed package (com.qcxr.qcxr.claudecraft), installs next to the official app with its own
#            data folder.
#
# Needs: java 17+, python3, zip/unzip, ANDROID_HOME (build-tools + a platform >= 34),
#        APKTOOL_JAR (apktool jar), DEX2JAR (path to d2j-dex2jar.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="" JAR="" VARIANT="" OUT="" TAG="dev"
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="$(realpath "$2")"; shift 2 ;;
    --jar) JAR="$(realpath "$2")"; shift 2 ;;
    --variant) VARIANT="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --tag) TAG="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[ -f "$APK" ] && [ -f "$JAR" ] && [ -n "$OUT" ] || { echo "need --apk, --jar and --out" >&2; exit 2; }
case "$VARIANT" in replace|side) ;; *) echo "--variant must be replace or side" >&2; exit 2 ;; esac
: "${ANDROID_HOME:?set ANDROID_HOME}" "${APKTOOL_JAR:?set APKTOOL_JAR}" "${DEX2JAR:?set DEX2JAR}"

BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
ANDROID_JAR="$(ls -d "$ANDROID_HOME"/platforms/android-* | sort -V | tail -1)/android.jar"
APKTOOL=(java -jar "$APKTOOL_JAR")
mkdir -p "$OUT"
OUT="$(realpath "$OUT")"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "build-tools: $BT"
echo "android.jar: $ANDROID_JAR"

# 1. Decode. "replace" keeps resources and manifest untouched (-r); "side" must edit the manifest.
if [ "$VARIANT" = replace ]; then
  "${APKTOOL[@]}" d -r -f "$APK" -o "$WORK/app" >/dev/null
else
  "${APKTOOL[@]}" d -f "$APK" -o "$WORK/app" >/dev/null
fi

# 2. Compile the patched launcher classes against the APK's own classes.
mapfile -t SOURCES < <(grep -v -E '^\s*(#|$)' "$ROOT/apk/patched-sources.txt" | sed "s#^#$ROOT/#")
if [ "${#SOURCES[@]}" -gt 0 ]; then
  "$DEX2JAR" -f -o "$WORK/apk-classes.jar" "$APK" >/dev/null 2>&1 || true
  [ -s "$WORK/apk-classes.jar" ] || { echo "dex2jar produced no classpath jar" >&2; exit 1; }
  mkdir -p "$WORK/classes"
  # android.jar has no java.lang.invoke.LambdaMetafactory, so javac stops on any lambda ("Fatal Error: Unable
  # to find method metafactory", exit 3). The build-tools ship stubs for it (AGP adds the same jar); d8 desugars.
  BOOTCP="$ANDROID_JAR"
  LAMBDA_STUBS="$(ls "$ANDROID_HOME"/build-tools/*/core-lambda-stubs.jar 2>/dev/null | sort -V | tail -1 || true)"
  [ -n "$LAMBDA_STUBS" ] && BOOTCP="$BOOTCP:$LAMBDA_STUBS"
  echo "javac bootclasspath: $BOOTCP"
  javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
    -bootclasspath "$BOOTCP" -cp "$WORK/apk-classes.jar" -implicit:none \
    -d "$WORK/classes" "${SOURCES[@]}"
  mkdir -p "$WORK/patchdex"
  mapfile -t CLASSFILES < <(find "$WORK/classes" -name '*.class')
  "$BT/d8" --release --min-api 29 --lib "$ANDROID_JAR" --classpath "$WORK/apk-classes.jar" \
    --output "$WORK/patchdex" "${CLASSFILES[@]}"

  # Turn our dex into smali by decoding a minimal APK that contains only it.
  mkdir -p "$WORK/mini"
  (cd "$WORK/mini" && unzip -q "$APK" AndroidManifest.xml && { unzip -q "$APK" resources.arsc || true; } && cp "$WORK/patchdex/classes.dex" .)
  (cd "$WORK/mini" && zip -q -r "$WORK/mini.apk" .)
  "${APKTOOL[@]}" d -r -f "$WORK/mini.apk" -o "$WORK/patchsmali" >/dev/null

  # Replace each patched top-level class (and its inner classes) in whichever smali dir holds it.
  echo "## Patched launcher classes" > "$OUT/patch-report-$VARIANT.md"
  mapfile -t TOPS < <(cd "$WORK/patchsmali/smali" && find . -name '*.smali' | sed 's#^\./##; s#\$.*##; s#\.smali$##' | sort -u)
  DEFAULT_DIR="$(dirname "$(dirname "$(find "$WORK/app" -path '*/smali*/pojlib/API.smali' | head -1)")")"
  for top in "${TOPS[@]}"; do
    orig="$(find "$WORK/app" -path "*/smali*/$top.smali" | head -1)"
    if [ -n "$orig" ]; then
      dir="${orig%/$top.smali}"
      mkdir -p "$WORK/orig/$(dirname "$top")"
      cp "$orig" "$WORK/orig/$top.smali"
      rm -f "$dir/$top.smali" "$dir/$top"\$*.smali
      status="replaced"
    else
      dir="$DEFAULT_DIR"; status="added"
    fi
    mkdir -p "$dir/$(dirname "$top")"
    cp "$WORK/patchsmali/smali/$top.smali" "$WORK/patchsmali/smali/$top"\$*.smali "$dir/$(dirname "$top")/" 2>/dev/null || \
      cp "$WORK/patchsmali/smali/$top.smali" "$dir/$(dirname "$top")/"
    echo "- \`$top\` ($status in \`${dir#$WORK/app/}\`)" >> "$OUT/patch-report-$VARIANT.md"
    if [ "$status" = replaced ]; then
      if ! diff <(grep -E '^\.(method|field)' "$WORK/orig/$top.smali" | sort) \
                <(grep -E '^\.(method|field)' "$dir/$top.smali" | sort) > "$WORK/sig.diff"; then
        { echo '  - member signatures differ from the APK (expected where we changed them):'; echo '```diff'; cat "$WORK/sig.diff"; echo '```'; } >> "$OUT/patch-report-$VARIANT.md"
      fi
    fi
  done
fi

# 3. Bundle our Vivecraft jar; the patched launcher installs it into the instance's mods folder.
mkdir -p "$WORK/app/assets/claudecraft"
cp "$JAR" "$WORK/app/assets/claudecraft/Vivecraft.jar"
{
  echo "build_tag=$TAG"
  echo "vivecraft_jar=$(basename "$JAR")"
  echo "mc_version=$(unzip -p "$JAR" fabric.mod.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("depends",{}).get("minecraft",""))' 2>/dev/null || true)"
} > "$WORK/app/assets/claudecraft/build.properties"

# 4. Side-by-side variant: rename the package, authorities, app-defined permissions and the label.
if [ "$VARIANT" = side ]; then
  python3 -I "$ROOT/apk/rename_package.py" "$WORK/app" com.qcxr.qcxr com.qcxr.qcxr.claudecraft "QuestCraft CC"
fi

# 5. Rebuild, align, sign.
"${APKTOOL[@]}" b "$WORK/app" -o "$WORK/unsigned.apk" >/dev/null
"$BT/zipalign" -p -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
NAME="questcraft-claudecraft-$TAG-$VARIANT.apk"
"$BT/apksigner" sign --ks "$ROOT/apk/claudecraft-debug.keystore" --ks-pass pass:claudecraft \
  --key-pass pass:claudecraft --ks-key-alias claudecraft --out "$OUT/$NAME" "$WORK/aligned.apk"
"$BT/apksigner" verify "$OUT/$NAME"
(cd "$OUT" && sha256sum "$NAME" | tee "$NAME.sha256")
