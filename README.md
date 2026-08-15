# tao Android repro — a user event sent before `run()` is not delivered

Minimal reproduction for: **on Android, an event sent through `EventLoopProxy`
before `event_loop.run()` is queued but never wakes the loop.** It arrives only
when some later event is sent, and then the whole backlog drains at once.

No wry, no webview, no Tauri. Just an event loop, a proxy and two events.

## What the app does

```
send user event 1        <- before run()
busy for 3000 ms         <- stands in for an app doing startup work
call event_loop.run()    <- event 1 is NOT delivered here
...
send user event 2 (+5 s) <- events 1 AND 2 both arrive, same millisecond
```

A heartbeat prints once a second so the log shows the loop is alive and waiting
rather than stuck.

The Kotlin side is the whole integration: `onFirstActivityCreate()` starts tao's
event-loop thread and `onStart()` reports the activity starting. Both are calls
any tao Android integration has to make — wry's generated `WryActivity` makes
them from a ProcessLifecycleOwner observer.

## Build and run

```sh
export JAVA_HOME=/path/to/jbr ANDROID_HOME=$HOME/Android/Sdk \
       NDK_HOME=$ANDROID_HOME/ndk/<version>

./build.sh                       # cargo build + stage the .so into jniLibs
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### On an emulator

It reproduces on a stock emulator, so no hardware is needed. Build the x86_64
library first, then install as above:

```sh
TARGET=x86_64-linux-android ./build.sh
cd android && ./gradlew assembleDebug
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
```

Verified on `sdk_gphone64_x86_64`, Android 15, API 35 — same result as the two
physical phones.

### Watching it happen

The app mirrors its own logcat on screen (`MainActivity.showOwnLog`, display
only — delete it and the behaviour is identical), so a plain screen recording
captures the screen and the live log together:

```sh
adb -e shell screenrecord --time-limit 14 /sdcard/taoraw.mp4
adb -e pull /sdcard/taoraw.mp4
```

Then:

```sh
adb shell pm clear org.repro.taoraw
adb logcat -c
adb shell am start -n org.repro.taoraw/.MainActivity
adb logcat -v time | grep 'taoraw +'
```

## The co-factor: traffic on the event pipe

The bug needs ndk_glue events pending alongside the queued user event. That is
why `MainActivity` calls `Rust.onStart(this)` — remove that one call and the
same user event is delivered in 1 ms, as it should be.

| pipe traffic before `run()` | delay before `run()` | event sent before `run()` |
| --- | --- | --- |
| no | none | delivered, +1 ms |
| no | 3 s | delivered, +1 ms after `run()` |
| **yes** (`onStart`) | 3 s | **lost until an unrelated later event** |

Neither the delay nor the blocking matters on its own. A real app produces this
traffic without trying: an activity starting up always does.

## Versions

Reproduces on **tao 0.36.0** (latest at the time of writing) and **0.35.3**.
Change the dependency in `Cargo.toml` to compare — note that 0.36 renamed the
JNI entry points (`create` → `onFirstActivityCreate`, `start` → `onStart`), so
`MainActivity.kt` needs the matching names, and 0.36 calls `activity.getId()`.

Measured on a Samsung Galaxy A22 5G, Android 13, aarch64 debug build.

## Testing the fix in PR #1304

The reproduction builds against the released crate on purpose, so it shows the
bug. To confirm the fix, copy the crate source, add the unconditional drain
from [PR #1304](https://github.com/tauri-apps/tao/pull/1304) after the
`match self.first_event.take()` block in
`src/platform_impl/android/mod.rs`, and point cargo at it:

```toml
[patch.crates-io]
tao = { path = "../tao-patched" }
```

Measured on a Galaxy A22 (Android 13), tao 0.36.0, same APK pipeline:

| | user event 1, sent before `run()` |
| --- | --- |
| released 0.36.0 | lost — arrives at +5003 ms, when event 2 is sent |
| + PR #1304 | **delivered at +3000 ms, as `run()` starts** |
