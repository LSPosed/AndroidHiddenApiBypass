# AndroidHiddenApiBypass

[![Android CI status](https://github.com/LSPosed/AndroidHiddenApiBypass/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/LSPosed/AndroidHiddenApiBypass/actions/workflows/android.yml)

Bypass restrictions on non-SDK interfaces.

## Why AndroidHiddenApiBypass?

- Pure Java: no native code used.
- Reliable: does not rely on specific behaviors, so it will not be blocked like meta-reflection or `dexfile`.
- Stable: `unsafe`, art structs and `setHiddenApiExemptions` are stable APIs.

[How it works (Chinese)](https://lovesykun.cn/archives/android-hidden-api-bypass.html)

## Integration

Gradle:

```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.lsposed.hiddenapibypass:hiddenapibypass:2.0'
}
```

## Usage

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    HiddenApiBypass.addHiddenApiExemptions("L");
}
```

## License

    Copyright 2021 LSPosed

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
