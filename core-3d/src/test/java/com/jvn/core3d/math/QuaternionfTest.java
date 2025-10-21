package com.jvn.core3d.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QuaternionfTest {

    @Test
    void testFromEulerIdentity() {
        Quaternionf q = Quaternionf.fromEulerDegrees(0, 0, 0);
        assertEquals(0f, q.x, 1e-6f);
        assertEquals(0f, q.y, 1e-6f);
        assertEquals(0f, q.z, 1e-6f);
        assertEquals(1f, q.w, 1e-6f);
    }

    @Test
    void testNormalize() {
        Quaternionf q = new Quaternionf(2, 0, 0, 0);
        Quaternionf n = q.normalize();
        assertEquals(1f, (float)Math.sqrt(n.x*n.x + n.y*n.y + n.z*n.z + n.w*n.w), 1e-6f);
    }

    @Test
    void testRotationTransformForward() {
        // 90 deg yaw around Y should rotate forward (0,0,-1) to (-1,0,0)
        Quaternionf q = Quaternionf.fromEulerDegrees(0, 90, 0);
        Vector3f fwd = new Vector3f(0, 0, -1);
        Vector3f r = q.transform(fwd);
        assertEquals(-1f, r.x, 1e-5f);
        assertEquals(0f, r.y, 1e-5f);
        assertEquals(0f, r.z, 1e-5f);
    }

    @Test
    void testSlerpMidpoint() {
        Quaternionf a = Quaternionf.fromEulerDegrees(0, 0, 0);
        Quaternionf b = Quaternionf.fromEulerDegrees(0, 180, 0);
        Quaternionf m = Quaternionf.slerp(a, b, 0.5f);
        // midpoint around Y ~ 90 deg yaw
        Vector3f fwd = new Vector3f(0, 0, -1);
        Vector3f r = m.transform(fwd);
        assertEquals(-1f, r.x, 1e-3f);
        assertEquals(0f, r.y, 1e-3f);
        assertEquals(0f, r.z, 1e-2f);
    }
}
