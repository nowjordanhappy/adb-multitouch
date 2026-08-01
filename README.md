# adb-multitouch

Two-finger **pinch**, **pan**, one-finger **drag** and tap over `adb` — **no root, no instrumented
test.**

`adb shell input` only does single-touch, and it can't hold a finger down before moving it. This is
a tiny `app_process` tool that injects real `MotionEvent`s through the framework
(`InputManager.injectInputEvent`) — the same privileged path `input` itself uses — so it works as
the **shell** user with **SELinux enforcing**: non-rootable emulators and physical devices alike.

## Getting started

You need `adb` on your `PATH` and a device with USB debugging on. **No build step** — the `mt.jar`
in this repo is prebuilt, and there's nothing to install on your machine beyond the repo itself.

```bash
git clone https://github.com/nowjordanhappy/adb-multitouch.git
cd adb-multitouch

adb devices                    # your device should be listed as "device", not "unauthorized"
./mt install                   # pushes mt.jar to /data/local/tmp (once per device)

./mt pinch 540 1170 300 1300   # pinch out on whatever is on screen right now
```

If the last line zooms something, you're done. Nothing is installed as an app, nothing persists
beyond a 3KB jar in `/data/local/tmp` — delete it with
`adb shell rm /data/local/tmp/mt.jar` when you're finished.

`./mt install` is optional: any command auto-pushes the jar if it's missing.

## Usage

```bash
./mt install                          # push mt.jar to the device (once)
./mt pinch 540 1170 300 1300          # pinch OUT (zoom in): finger gap 300px -> 1300px around (540,1170)
./mt pinch 540 1170 1300 300          # pinch IN  (zoom out)
./mt pan   540 1170 0 -600            # two-finger drag up by 600px
./mt drag  416 993 540 220            # press, HOLD, drag to (540,220), release — e.g. drag-to-delete
./mt tap   540 1170
./mt pinch 540 1170 300 1300 30 800   # slower, smoother: 30 steps over 800ms
./mt -s emulator-5556 pinch 540 1170 300 1300   # target a specific device
./mt                                  # no args: print this usage
```

### What the numbers mean

```
pinch <cx> <cy> <startGap> <endGap> [steps] [ms]
pan   <cx> <cy> <dx> <dy>           [steps] [ms]
drag  <x0> <y0> <x1> <y1>           [holdMs] [steps] [ms]
tap   <x> <y>
```

| | |
|---|---|
| `cx` `cy` | Where the gesture is centred, in **screen pixels** — same coordinate space as `input tap`. `adb shell wm size` prints the screen; the middle of a 1080×2400 phone is `540 1200`. |
| `startGap` `endGap` | How far apart the two fingers are, in pixels, at the start and at the end. **End bigger than start = pinch out = zoom in**; the other way round zooms out. |
| `dx` `dy` | How far both fingers travel, in pixels. `y` grows **downward**, so a negative `dy` drags up — `0 -600` is a 600px upward drag. |
| `x0` `y0` → `x1` `y1` | For `drag`, where the one finger starts and ends — grab point to drop point. |
| `holdMs` | For `drag`, how long the finger stays still after touching down (default `600`). This is the whole point of the command: launchers and lists enter drag mode on a **long-press**, so the hold has to outlast the platform's long-press timeout (~500ms). |
| `steps` | How many MOVE events the gesture is split into (default `12`). More steps = smoother, which some apps need to track the gesture at all. |
| `ms` | Total duration of the gesture (default `300`). |

`drag` is one finger, not two — it's here because `input` can't express it either. `input swipe`
starts moving immediately, so the view never long-presses into drag mode and the gesture reads as a
fling; `input draganddrop` exists but did nothing on Launcher3. Use it for home screen
drag-and-drop, drag-to-delete, and list reordering:

```bash
./mt drag 416 993 540 220                 # Launcher3: widget onto "Remove" (defaults are enough)
./mt drag 540 1866 540 150 2000 30 3000   # MIUI: same drag to the top, but needs a ~2s hold
```

After the last MOVE the finger pauses briefly before the UP, because drop targets only highlight on
hover and releasing instantly lands on nothing.

**The defaults are tuned for Launcher3; OEM launchers are slower.** On MIUI 14 the default 600ms
hold raises the edit-mode popup, but the widget doesn't follow the finger — the MOVEs arrive while
the lift animation is still running and get dropped. `2000 30 3000` (2s hold, 30 steps over 3s)
both moves and removes reliably. If a drag does nothing, **raise the hold** before concluding the
gesture is unsupported: `exit=0` means the events were delivered, not that the launcher acted on
them. Nearly every "MIUI doesn't support this" dead end here turned out to be a hold that was too
short.

For `pinch` the two fingers are placed **vertically**, `gap/2` above and below `cy`. So
`pinch 540 1170 300 1300` ends with fingers at y=520 and y=1820 — keep `cy ± endGap/2` on screen, or
the gesture runs off the edge and the app sees something odd. For `pan` they sit 240px apart
horizontally and move together.

Exits non-zero if the framework rejects the injection, so it's safe to chain with `&&` in scripts.

## Driving it from a script (or an agent)

Every gesture is one line, takes absolute screen pixels, and exits non-zero if the framework
rejects the injection — so it scripts like any other shell command. The coordinates come from the
device itself, which closes the loop:

```bash
# 1. dump the screen, find the thing you want to touch
adb shell uiautomator dump /sdcard/ui.xml
adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -i AppWidgetHostView
#   ... content-desc="Weather" bounds="[314,542][766,815]"

# 2. aim at its centre and gesture
./mt drag 540 678 540 150 2000 30 3000     # drag it to the top of the screen

# 3. verify from the device, not from the screenshot
adb shell dumpsys appwidget | grep -c com.example.app     # one fewer than before
```

**Dump → aim → gesture → verify by dumping again.** The last step is the one people skip: `exit=0`
means the events were *delivered*, not that the app acted on them, so the result has to be read
back — a dump, a `dumpsys` count, a logcat line. Vary the timing before concluding a gesture is
unsupported (see the hold caveat above).

That loop is also what makes this usable by a coding agent: it can run each step and check the
output, but it has no way to touch a screen.

## Demo

Google Maps, pinched from the command line — nothing about the app is modified or instrumented:

<img src="assets/maps.gif" width="300" alt="Google Maps zooming in and out of New York, driven by two-finger pinch gestures injected over adb">

It works the same in any app. Below is [Strux — IFC & BIM
Viewer](https://play.google.com/store/apps/details?id=com.nowjordanhappy.strux), which is what this
tool was built for: pinch-to-zoom and two-finger pan are its whole interaction model, and there was
no way to exercise them from `adb`.

<img src="assets/demo.gif" width="300" alt="A 3D building model zooming and panning, driven by two-finger gestures injected over adb">

Every frame in both is driven by `./mt` — no touchscreen, no root, no `am instrument`.

## Why this works without root

`adb shell input` is itself a Java program run via `app_process` as the shell user, calling
`InputManager.injectInputEvent`. It's single-touch **only because its CLI doesn't expose more** — not
because of a permission limit. This tool does the same injection but assembles **two-pointer** events
(`ACTION_POINTER_DOWN` / `ACTION_MOVE` / `ACTION_POINTER_UP`) — and, for `drag`, a single pointer
that *holds still* before it moves, which the `input` CLI has no way to express. No `/dev/input`
writes (SELinux blocks those for shell), no root, no `am instrument`.

## Caveats

- **MIUI / some OEMs:** enable *Developer options → USB debugging (Security settings)* (it lets adb
  simulate input). It silently resets itself; if injection does nothing, re-check it.
- Injects into whatever window is focused (the coordinates are absolute screen pixels).
- **Let the app settle between gestures.** Fire a second gesture while the app is still animating the
  first (Maps' zoom easing, a fling, a camera move it started itself) and it may swallow it, or read
  a pinch as a drag. Injection still succeeds and exits 0 — the app just ignored it. ~2s apart is
  reliable; 1s was not.
- **`drag` depends on where the drop target is.** Launcher3 and MIUI 14 both put it at the **top of
  the screen**, so dragging there removes. (MIUI *also* floats a "Remove" menu button beside the
  widget on long-press — that's an edit-mode affordance, not the drop target; ignore it and keep
  dragging to the top.) Other launchers may differ: dump the screen mid-drag and aim.
- Verified on a **physical Xiaomi Redmi Note 11 (Android 13, MIUI V140)** and on Android 11 and
  Android 16 (API 36) emulators — **shell + SELinux Enforcing + no root** in every case. `drag`
  specifically: removed *and* repositioned a home screen widget on both Launcher3 (API 36 emulator)
  and MIUI 14 — the latter with the longer timings above.
- Android 14 moved injection onto `InputManagerGlobal`; the tool tries that first and falls back to
  `InputManager` on older releases. The fallback is what the Xiaomi above exercises — the
  `InputManagerGlobal` branch has so far only been run on the API 36 emulator.

## Build

Prebuilt `mt.jar` is committed. To rebuild:

```bash
ANDROID_HOME=~/Library/Android/sdk ./build.sh   # javac --release 11 + d8 --min-api 26
```

The committed jar was built with android-36 / build-tools 36.0.0 (`build.sh` picks the newest it finds).

## Tests

```bash
./test.sh   # pure JVM, no device, no android.jar
```

Injection needs a real device (verify it by eye — a pinch zooms, a pan drags). What *is*
unit-testable without one is the **gesture geometry** — where the two fingers are placed each frame.
That lives in `Gestures.java` (no android deps), the shipped tool consumes it, and `GesturesTest`
asserts the interpolation endpoints, symmetry, finger spacing and the one-finger `drag` line.

## How it's structured

- `Gestures.java` — pure two-pointer frame maths (testable off-device).
- `MultiTouch.java` — the tool: parses `pinch|pan|drag|tap`, turns frames into `MotionEvent`s, injects
  via reflection so it needs no hidden-API stubs at compile time.
- `GesturesTest.java` — runnable assert-based self-check (no framework).
- `mt` — bash wrapper (push + `app_process` invocation).
- `build.sh` / `test.sh` — `javac` + `d8` / run the checks.
- `mt.jar` — the prebuilt dex.

## License

MIT
