# adb-multitouch

Two-finger **pinch**, **pan** and tap over `adb` — **no root, no instrumented test.**

`adb shell input` only does single-touch. This is a tiny `app_process` tool that injects real
multi-pointer `MotionEvent`s through the framework (`InputManager.injectInputEvent`) — the same
privileged path `input` itself uses — so it works as the **shell** user with **SELinux enforcing**:
non-rootable emulators and physical devices alike.

```bash
./mt install                          # push mt.jar to the device (once)
./mt pinch 540 1170 300 1300          # pinch OUT (zoom in): finger gap 300px -> 1300px around (540,1170)
./mt pinch 540 1170 1300 300          # pinch IN  (zoom out)
./mt pan   540 1170 0 -600            # two-finger drag up by 600px
./mt tap   540 1170
./mt -s emulator-5556 pinch 540 1170 300 1300   # target a specific device
```

Coordinates are **screen pixels**, like `input tap`. Optional trailing `[steps] [ms]` control smoothness
and duration (defaults `12 350`).

## Why this works without root

`adb shell input` is itself a Java program run via `app_process` as the shell user, calling
`InputManager.injectInputEvent`. It's single-touch **only because its CLI doesn't expose more** — not
because of a permission limit. This tool does the same injection but assembles **two-pointer** events
(`ACTION_POINTER_DOWN` / `ACTION_MOVE` / `ACTION_POINTER_UP`). No `/dev/input` writes (SELinux blocks
those for shell), no root, no `am instrument`.

## Caveats

- **MIUI / some OEMs:** enable *Developer options → USB debugging (Security settings)* (it lets adb
  simulate input). It silently resets itself; if injection does nothing, re-check it.
- Injects into whatever window is focused (the coordinates are absolute screen pixels).
- Verified: Android 11 emulator (google_apis), **shell + SELinux Enforcing + no root**.

## Build

Prebuilt `mt.jar` is committed. To rebuild:

```bash
ANDROID_HOME=~/Library/Android/sdk ./build.sh   # javac --release 8 + d8
```

## How it's structured

- `MultiTouch.java` — the tool (`main` parses `pinch|pan|tap`, builds `MotionEvent`s, injects via
  reflection so it needs no hidden-API stubs at compile time).
- `mt` — bash wrapper (push + `app_process` invocation).
- `build.sh` — `javac` + `d8`.
- `mt.jar` — the prebuilt dex.

## License

MIT
