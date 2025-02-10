#!/usr/bin/env bash

set -xe
export PATH="$PATH:$ANDROID_HOME/platform-tools"
sdk="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
cvd_args="-daemon -enable_sandbox=false -memory_mb=8192 -report_anonymous_usage_stats=n"

cleanup() {
  print_error "! An error occurred"
  run_cvd_bin stop_cvd || true
}

run_cvd_bin() {
  local exe=$1
  shift
  HOME=$CF_HOME $CF_HOME/bin/$exe "$@"
}

setup_env() {
  curl -LO https://github.com/user-attachments/files/18728876/cuttlefish-base_1.2.0_amd64.zip
  sudo dpkg -i ./cuttlefish-base_*_*64.zip || sudo apt-get install -f
  rm cuttlefish-base_*_*64.zip
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
  sudo udevadm control --reload-rules
  sudo udevadm trigger
  sudo usermod -aG kvm,cvdnetwork,render $USER
  yes | "$sdk" --licenses > /dev/null
  "$sdk" --channel=3 platform-tools
}

download_cf() {
  local branch=$1
  local device=$2

  if [ -z $branch ]; then
    branch='aosp-main'
  fi
  if [ -z $device ]; then
    device='aosp_cf_x86_64_phone'
  fi
  local target="${device}-trunk_staging-userdebug"

  local build_id=$(curl -sL https://ci.android.com/builds/branches/${branch}/status.json | \
    jq -r ".targets[] | select(.name == \"$target\") | .last_known_good_build")
  local sys_img_url="https://ci.android.com/builds/submitted/${build_id}/${target}/latest/raw/${device}-img-${build_id}.zip"
  local host_pkg_url="https://ci.android.com/builds/submitted/${build_id}/${target}/latest/raw/cvd-host_package.tar.gz"

  curl -L $sys_img_url -o aosp_cf_phone-img.zip
  curl -LO $host_pkg_url
  rm -rf $CF_HOME
  mkdir -p $CF_HOME
  tar xvf cvd-host_package.tar.gz -C $CF_HOME
  unzip aosp_cf_phone-img.zip -d $CF_HOME
  rm -f cvd-host_package.tar.gz aosp_cf_phone-img.zip
}


test_main() {
  run_cvd_bin launch_cvd $cvd_args
  adb wait-for-device
  ./gradlew connectedCheck
  run_cvd_bin stop_cvd || true
}

if [ -z $CF_HOME ]; then
  print_error "! Environment variable CF_HOME is required"
  exit 1
fi

case "$1" in
  setup )
    setup_env
    ;;
  download )
    download_cf $2 $3
    ;;
  test )
    trap cleanup EXIT
    export -f run_cvd_bin
    test_main
    trap - EXIT
    ;;
  * )
    exit 1
    ;;
esac
