#!/usr/bin/env bash
set -euo pipefail

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

adb="$sdk/platform-tools/adb"
if [ -x "$adb" ] && [ ! -e "$adb.real" ]; then
  mv "$adb" "$adb.real"
  cat > "$adb" <<'EOF'
#!/usr/bin/env bash
set -u

adb="$0.real"
if [ ! -x "$adb" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  adb="$sdk/platform-tools/adb.real"
fi

case " $* " in
  *" shell input keyevent 82 "*|*" shell settings put "*)
    ;;
  *)
    exec "$adb" "$@"
    ;;
esac

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

status=1
for attempt in $(seq 1 20); do
  "$adb" "$@" >"$tmp" 2>&1
  status=$?
  cat "$tmp"
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  if ! grep -q 'Broken pipe (32)\|Cannot broadcast before boot completed\|Can'\''t find service: input\|Can'\''t find service: settings' "$tmp"; then
    exit "$status"
  fi
  sleep 2
done

exit "$status"
EOF
  chmod +x "$adb"
fi

if [ "${1:-}" != "capture-console" ]; then
  exit 0
fi

emulator="$sdk/emulator/emulator"
log=/tmp/emulator-console.log
if [ ! -x "$emulator" ]; then
  exit 0
fi
if [ -e "$emulator.real" ]; then
  exit 0
fi
mv "$emulator" "$emulator.real"
cat > "$emulator" <<EOF
#!/usr/bin/env bash
"$emulator.real" "\$@" 2>&1 | tee -a $log
EOF
chmod +x "$emulator"
