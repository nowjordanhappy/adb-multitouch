package com.nowjordanhappy.mt;

/**
 * Pure two-pointer gesture geometry — no android deps, so it unit-tests on a plain JVM (the injection
 * that consumes these frames needs a device; the maths that places the fingers does not).
 *
 * Each method returns steps+1 frames; a frame is {x0, y0, x1, y1} (the two finger positions). The
 * caller sends frame[0] as the DOWN, frames[1..steps] as MOVEs, and frame[steps] as the UP.
 */
public final class Gestures {
    private Gestures() {}

    /** Vertical pinch about (cx, cy): finger 0 at (cx, cy-g/2), finger 1 at (cx, cy+g/2); gap g0→g1. */
    public static float[][] pinch(float cx, float cy, float g0, float g1, int steps) {
        float[][] f = new float[steps + 1][];
        for (int s = 0; s <= steps; s++) {
            float g = g0 + (g1 - g0) * s / steps;
            f[s] = new float[]{cx, cy - g / 2, cx, cy + g / 2};
        }
        return f;
    }

    /** Two-finger pan: two fingers {@value #PAN_SPACING}px apart about cx, both translated by (dx, dy). */
    public static float[][] pan(float cx, float cy, float dx, float dy, int steps) {
        float ax = cx - PAN_SPACING / 2f, bx = cx + PAN_SPACING / 2f;
        float[][] f = new float[steps + 1][];
        for (int s = 0; s <= steps; s++) {
            float fx = dx * s / steps, fy = dy * s / steps;
            f[s] = new float[]{ax + fx, cy + fy, bx + fx, cy + fy};
        }
        return f;
    }

    public static final float PAN_SPACING = 240f;
}
