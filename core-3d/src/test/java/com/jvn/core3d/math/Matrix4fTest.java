package com.jvn.core3d.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Matrix4fTest {

    @Test
    void testIdentityMultiply() {
        Matrix4f a = Matrix4f.identity();
        Matrix4f b = Matrix4f.identity();
        Matrix4f c = Matrix4f.multiply(a, b);
        assertArrayEquals(a.toArray(), c.toArray(), 1e-6f);
    }

    @Test
    void testTranslationComposition() {
        Matrix4f t1 = Matrix4f.translation(1, 2, 3);
        Matrix4f t2 = Matrix4f.translation(-2, 0.5f, 4);
        Matrix4f r = Matrix4f.multiply(t1, t2);
        float[] m = r.toArray();
        assertEquals(-1.0f, m[12], 1e-6f);
        assertEquals(2.5f, m[13], 1e-6f);
        assertEquals(7.0f, m[14], 1e-6f);
    }

    @Test
    void testLookAtOrthogonality() {
        Vector3f eye = new Vector3f(0, 0, 5);
        Vector3f center = new Vector3f(0, 0, 0);
        Vector3f up = new Vector3f(0, 1, 0);
        Matrix4f view = Matrix4f.lookAt(eye, center, up);
        float[] m = view.toArray();
        // Upper-left 3x3 should be orthonormal; check row lengths ~1
        float r0 = (float)Math.sqrt(m[0]*m[0] + m[1]*m[1] + m[2]*m[2]);
        float r1 = (float)Math.sqrt(m[4]*m[4] + m[5]*m[5] + m[6]*m[6]);
        float r2 = (float)Math.sqrt(m[8]*m[8] + m[9]*m[9] + m[10]*m[10]);
        assertEquals(1.0f, r0, 1e-4);
        assertEquals(1.0f, r1, 1e-4);
        assertEquals(1.0f, r2, 1e-4);
    }
}
