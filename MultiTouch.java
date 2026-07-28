package com.strux.mt;

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
 *   adb shell CLASSPATH=/data/local/tmp/mt.jar app_process /system/bin com.strux.mt.MultiTouch \
 *       pinch  <cx> <cy> <startGap> <endGap> [steps] [ms]
 *       pan    <cx> <cy> <dx> <dy>           [steps] [ms]
 *       tap    <x> <y>
 */
public final class MultiTouch {
    private static Object inputManager;
    private static Method inject;
    private static final int MODE_ASYNC = 0; // INJECT_INPUT_EVENT_MODE_ASYNC

    public static void main(String[] a) throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", InputEvent.class, int.class);

        if (a.length == 0) { usage(); return; }
        switch (a[0]) {
            case "tap":   tap(f(a,1), f(a,2)); break;
            case "pinch": pinch(f(a,1), f(a,2), f(a,3), f(a,4), i(a,5,12), i(a,6,300)); break;
            case "pan":   pan(f(a,1), f(a,2), f(a,3), f(a,4), i(a,5,12), i(a,6,300)); break;
            default: usage();
        }
    }

    private static void usage() {
        System.err.println("usage: tap x y | pinch cx cy startGap endGap [steps] [ms] | pan cx cy dx dy [steps] [ms]");
    }

    private static float f(String[] a, int i) { return Float.parseFloat(a[i]); }
    private static int i(String[] a, int i, int def) { return a.length > i ? Integer.parseInt(a[i]) : def; }
    private static void nap(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private static void send(MotionEvent e) throws Exception {
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject.invoke(inputManager, e, MODE_ASYNC);
        e.recycle();
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
        long down = SystemClock.uptimeMillis();
        send(one(down, down, MotionEvent.ACTION_DOWN, cx, cy - g0 / 2));
        send(two(down, down, PTR1_DOWN, cx, cy - g0 / 2, cx, cy + g0 / 2));
        for (int s = 1; s <= steps; s++) {
            float g = g0 + (g1 - g0) * s / steps;
            send(two(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, cx, cy - g / 2, cx, cy + g / 2));
            nap(Math.max(1, dur / steps));
        }
        send(two(down, SystemClock.uptimeMillis(), PTR1_UP, cx, cy - g1 / 2, cx, cy + g1 / 2));
        send(one(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cx, cy - g1 / 2));
    }

    /** Two-finger pan: two fingers 240px apart, both translated by (dx, dy). */
    private static void pan(float cx, float cy, float dx, float dy, int steps, int dur) throws Exception {
        long down = SystemClock.uptimeMillis();
        float ax = cx - 120, bx = cx + 120, ay = cy, by = cy;
        send(one(down, down, MotionEvent.ACTION_DOWN, ax, ay));
        send(two(down, down, PTR1_DOWN, ax, ay, bx, by));
        for (int s = 1; s <= steps; s++) {
            float fx = dx * s / steps, fy = dy * s / steps;
            send(two(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, ax + fx, ay + fy, bx + fx, by + fy));
            nap(Math.max(1, dur / steps));
        }
        send(two(down, SystemClock.uptimeMillis(), PTR1_UP, ax + dx, ay + dy, bx + dx, by + dy));
        send(one(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, ax + dx, ay + dy));
    }
}
