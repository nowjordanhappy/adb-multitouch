package com.nowjordanhappy.mt;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import java.lang.reflect.Method;

/**
 * Multi-touch injection over adb, no root — the same path {@code adb shell input} uses (an app_process
 * program run as the shell user calling InputManager.injectInputEvent), but exposing TWO-finger events
 * that the stock `input` CLI doesn't. Coordinates are screen pixels, like `input tap`.
 *
 *   adb shell CLASSPATH=/data/local/tmp/mt.jar app_process /system/bin com.nowjordanhappy.mt.MultiTouch \
 *       pinch  <cx> <cy> <startGap> <endGap> [steps] [ms]
 *       pan    <cx> <cy> <dx> <dy>           [steps] [ms]
 *       drag   <x0> <y0> <x1> <y1>           [holdMs] [steps] [ms]
 *       tap    <x> <y>
 */
public final class MultiTouch {
    private static Object inputManager;
    private static Method inject;
    private static final int MODE_ASYNC = 0; // INJECT_INPUT_EVENT_MODE_ASYNC

    public static void main(String[] a) throws Exception {
        // Android 14 moved injection off InputManager onto InputManagerGlobal (same method, same path).
        Class<?> im;
        try { im = Class.forName("android.hardware.input.InputManagerGlobal"); }
        catch (ClassNotFoundException pre14) { im = Class.forName("android.hardware.input.InputManager"); }
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);

        if (a.length == 0) { usage(); return; }
        switch (a[0]) {
            case "tap":   tap(f(a,1), f(a,2)); break;
            case "pinch": pinch(f(a,1), f(a,2), f(a,3), f(a,4), i(a,5,12), i(a,6,300)); break;
            case "pan":   pan(f(a,1), f(a,2), f(a,3), f(a,4), i(a,5,12), i(a,6,300)); break;
            case "drag":  drag(f(a,1), f(a,2), f(a,3), f(a,4), i(a,5,600), i(a,6,12), i(a,7,600)); break;
            default: usage();
        }
    }

    private static void usage() {
        System.err.println("usage: tap x y | pinch cx cy startGap endGap [steps] [ms] | pan cx cy dx dy [steps] [ms]"
                + " | drag x0 y0 x1 y1 [holdMs] [steps] [ms]");
        System.exit(2);
    }

    private static float f(String[] a, int i) { return Float.parseFloat(a[i]); }
    private static int i(String[] a, int i, int def) { return a.length > i ? Integer.parseInt(a[i]) : def; }
    private static void nap(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private static void send(MotionEvent e) throws Exception {
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        Object ok = inject.invoke(inputManager, e, MODE_ASYNC);
        e.recycle();
        if (Boolean.FALSE.equals(ok)) {
            System.err.println("injection rejected — MIUI/OEM: enable Developer options -> USB debugging (Security settings)");
            System.exit(1);
        }
    }

    private static PointerProperties prop(int id) {
        PointerProperties p = new PointerProperties();
        p.id = id; p.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return p;
    }
    private static PointerCoords coord(float x, float y) {
        PointerCoords c = new PointerCoords();
        c.x = x; c.y = y; c.pressure = 1f; c.size = 1f;
        return c;
    }

    private static MotionEvent one(long down, long when, int action, float x, float y) {
        return MotionEvent.obtain(down, when, action, 1,
                new PointerProperties[]{prop(0)}, new PointerCoords[]{coord(x, y)},
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }
    private static MotionEvent two(long down, long when, int action, float x0, float y0, float x1, float y1) {
        return MotionEvent.obtain(down, when, action, 2,
                new PointerProperties[]{prop(0), prop(1)},
                new PointerCoords[]{coord(x0, y0), coord(x1, y1)},
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private static final int PTR1_DOWN = MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    private static final int PTR1_UP   = MotionEvent.ACTION_POINTER_UP   | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);

    private static void tap(float x, float y) throws Exception {
        long t = SystemClock.uptimeMillis();
        send(one(t, t, MotionEvent.ACTION_DOWN, x, y));
        send(one(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y));
    }

    /** Vertical pinch: finger 0 at (cx, cy-gap/2), finger 1 at (cx, cy+gap/2); gap goes startGap→endGap. */
    private static void pinch(float cx, float cy, float g0, float g1, int steps, int dur) throws Exception {
        playTwoFinger(Gestures.pinch(cx, cy, g0, g1, steps), dur);
    }

    /** Two-finger pan: two fingers a fixed distance apart, both translated by (dx, dy). */
    private static void pan(float cx, float cy, float dx, float dy, int steps, int dur) throws Exception {
        playTwoFinger(Gestures.pan(cx, cy, dx, dy, steps), dur);
    }

    /**
     * One-finger press-hold-move-release — launcher drag-and-drop, drag-to-delete, reordering.
     * `input swipe` can't express this: it starts moving immediately, so the view never long-presses
     * into drag mode and the gesture reads as a fling.
     */
    private static void drag(float x0, float y0, float x1, float y1, int hold, int steps, int dur) throws Exception {
        float[][] frames = Gestures.drag(x0, y0, x1, y1, steps);
        long down = SystemClock.uptimeMillis();
        send(one(down, down, MotionEvent.ACTION_DOWN, frames[0][0], frames[0][1]));
        nap(hold); // the point of the whole command — must outlast the platform long-press timeout
        for (int s = 1; s <= steps; s++) {
            send(one(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, frames[s][0], frames[s][1]));
            nap(Math.max(1, dur / steps));
        }
        float[] z = frames[steps];
        nap(SETTLE_MS); // drop targets highlight on hover; releasing instantly lands on nothing
        send(one(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, z[0], z[1]));
    }

    private static final int SETTLE_MS = 250;

    /** Send a frame list ({x0,y0,x1,y1} rows) as DOWN → MOVEs → UP over the given duration. */
    private static void playTwoFinger(float[][] frames, int dur) throws Exception {
        long down = SystemClock.uptimeMillis();
        float[] a = frames[0];
        send(one(down, down, MotionEvent.ACTION_DOWN, a[0], a[1]));
        send(two(down, down, PTR1_DOWN, a[0], a[1], a[2], a[3]));
        int steps = frames.length - 1;
        for (int s = 1; s <= steps; s++) {
            float[] fr = frames[s];
            send(two(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, fr[0], fr[1], fr[2], fr[3]));
            nap(Math.max(1, dur / steps));
        }
        float[] z = frames[steps];
        send(two(down, SystemClock.uptimeMillis(), PTR1_UP, z[0], z[1], z[2], z[3]));
        send(one(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, z[0], z[1]));
    }
}
