package com.strux.mt;

/** Runnable self-check for the gesture geometry. `javac Gestures.java GesturesTest.java && java -ea com.strux.mt.GesturesTest`. */
public class GesturesTest {
    public static void main(String[] a) {
        // --- pinch ---
        float[][] p = Gestures.pinch(540, 1200, 300, 1600, 16);
        eq(17, p.length, "pinch frame count = steps+1");
        eq(300f, gap(p[0]), "pinch starts at g0");
        eq(1600f, gap(p[16]), "pinch ends at g1");
        eq(950f, gap(p[8]), "pinch gap interpolates linearly at the midpoint");
        for (float[] fr : p) {
            eq(540f, fr[0], "pinch finger 0 stays on cx");
            eq(540f, fr[2], "pinch finger 1 stays on cx");
            eq(2400f, fr[1] + fr[3], "pinch stays symmetric about cy");
        }

        // --- pan ---
        float[][] q = Gestures.pan(540, 1000, 0, 700, 16);
        eq(17, q.length, "pan frame count = steps+1");
        eq(240f, q[0][2] - q[0][0], "pan fingers start PAN_SPACING apart");
        eq(240f, q[16][2] - q[16][0], "pan fingers stay PAN_SPACING apart");
        eq(1000f, q[0][1], "pan starts at cy");
        eq(1700f, q[16][1], "pan finger 0 translated by dy");
        eq(1700f, q[16][3], "pan finger 1 translated by dy");
        eq(0f, q[16][0] - q[0][0], "pan honours dx=0");
        eq(350f, q[8][1] - q[0][1], "pan translates linearly at the midpoint");

        // --- degenerate: 1 step still yields a start and an end ---
        float[][] r = Gestures.pinch(0, 0, 100, 200, 1);
        eq(2, r.length, "steps=1 gives start+end");
        eq(100f, gap(r[0]), "steps=1 start gap");
        eq(200f, gap(r[1]), "steps=1 end gap");

        System.out.println("OK — all gesture-geometry checks passed");
    }

    private static float gap(float[] fr) { return Math.abs(fr[3] - fr[1]); }
    private static void eq(float want, float got, String msg) {
        if (Math.abs(want - got) > 1e-4) throw new AssertionError(msg + " (want " + want + ", got " + got + ")");
    }
}
