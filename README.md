# AndroidHiddenApiBypass

[![Android CI status](https://github.com/LSPosed/AndroidHiddenApiBypass/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/LSPosed/AndroidHiddenApiBypass/actions/workflows/android.yml)
![](https://img.shields.io/badge/Android-1.0%20--%2016-blue.svg)
![](https://img.shields.io/maven-central/v/org.lsposed.hiddenapibypass/hiddenapibypass.svg)

Bypass restrictions on non-SDK interfaces.

## Why AndroidHiddenApiBypass?

- Pure Java: no native code used.
- Reliable: does not rely on specific behaviors, so it will not be blocked like meta-reflection or `dexfile`.
- Stable: does not rely on internal ART structures on Android 10+. `unsafe` and `setHiddenApiExemptions` are stable APIs.

## How it works

[Variant 1 (Chinese)](https://lovesykun.cn/archives/android-hidden-api-bypass.html)

[Variant 2](https://github.com/michalbednarski/LeakValue?tab=readme-ov-file#putting-it-all-together)

## Integration

Gradle:

```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.lsposed.hiddenapibypass:hiddenapibypass:+'
}
```

## Usage

This library has two variants of bypassing. Variant 2 is faster than variant 1 but maybe blocked in future Android releases. We recommend to use variant 1 for most usecases. Check `LSPass` class for API definition if you want to use variant 2.

1. Invoke a restricted method:
    ```java
    HiddenApiBypass.invoke(ApplicationInfo.class, new ApplicationInfo(), "usesNonSdkApi"/*, args*/)
    ```
1. Invoke restricted constructor:
    ```java
    Object instance = HiddenApiBypass.newInstance(Class.forName("android.app.IActivityManager$Default")/*, args*/);
    ```
1. Get all methods including restricted ones from a class:
    ```java
    var allMethods = HiddenApiBypass.getDeclaredMethods(ApplicationInfo.class);
    ((Method).stream(allMethods).filter(e -> e.getName().equals("usesNonSdkApi")).findFirst().get()).invoke(new ApplicationInfo());
    ```
1. Get all non-static fields including restricted ones from a class:
    ```java
    var allInstanceFields = HiddenApiBypass.getInstanceFields(ApplicationInfo.class);
    ((Method).stream(allInstanceFields).filter(e -> e.getName().equals("longVersionCode")).findFirst().get()).get(new ApplicationInfo());
    ```
1. Get all static fields including restricted ones from a class:
    ```java
    var allStaticFields = HiddenApiBypass.getStaticFields(ApplicationInfo.class);
    ((Method).stream(allStaticFields).filter(e -> e.getName().equals("HIDDEN_API_ENFORCEMENT_DEFAULT")).findFirst().get()).get(null);
    ```
1. Get specific class method or class constructor
    ```java
    var ctor = HiddenApiBypass.getDeclaredConstructor(ClipDrawable.class /*, args */);
    var method = HiddenApiBypass.getDeclaredMethod(ApplicationInfo.class, "getHiddenApiEnforcementPolicy" /*, args */);
    ```
1. Add a class to exemption list:
    ```java
    HiddenApiBypass.addHiddenApiExemptions(
        "Landroid/content/pm/ApplicationInfo;", // one specific class
        "Ldalvik/system" // all classes in packages dalvik.system
        "Lx" // all classes whose full name is started with x
    );
    ```
    if you are going to add all classes to exemption list, just leave an empty prefix:
    ```java
    HiddenApiBypass.addHiddenApiExemptions("");
    ```

## License

    Copyright 2021-2025 LSPosed

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
