#!/bin/sh
# Tee the emulator console to /tmp/emulator-console.log, because when the guest never boots
# (16 KB page size images) adb cannot attach and the console output is the only evidence left.
# Wired through the emulator-runner's pre-emulator-launch-script input, for ps16k images only.
set -eu

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
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
#!/bin/sh
"$emulator.real" "\$@" 2>&1 | tee -a $log
EOF
chmod +x "$emulator"
