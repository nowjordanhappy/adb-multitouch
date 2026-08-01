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

    /**
     * Single-finger straight line from (x0,y0) to (x1,y1). Frames are {x, y} — one pointer, so half
     * the width of the two-finger rows above. What makes this a <em>drag</em> rather than a swipe is
     * the caller holding after the DOWN; the geometry is the same either way.
     */
    public static float[][] drag(float x0, float y0, float x1, float y1, int steps) {
        float[][] f = new float[steps + 1][];
        for (int s = 0; s <= steps; s++) {
            f[s] = new float[]{x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps};
        }
        return f;
    }

    public static final float PAN_SPACING = 240f;
}
