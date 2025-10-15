package com.jvn.core3d.scene;

import com.jvn.core3d.math.Matrix4f;
import com.jvn.core3d.math.Quaternionf;
import com.jvn.core3d.math.Vector3f;

public class Camera {
    private final Transform transform = new Transform();

    private float fovYDegrees = 60.0f;
    private float aspect = 16f / 9f;
    private float near = 0.1f;
    private float far = 1000f;

    public Transform getTransform() { return transform; }

    public void setPerspective(float fovYDegrees, float aspect, float near, float far) {
        this.fovYDegrees = fovYDegrees;
        this.aspect = aspect;
        this.near = near;
        this.far = far;
    }

    public Matrix4f getViewMatrix() {
        Vector3f eye = transform.getPosition();
        Vector3f forward = transform.forward();
        Vector3f up = transform.up();
        return Matrix4f.lookAt(eye, eye.add(forward), up);
    }

    public Matrix4f getProjectionMatrix() {
        float fovRadians = (float) (Math.PI / 180.0) * fovYDegrees;
        return Matrix4f.perspective(fovRadians, aspect, near, far);
    }

    public Vector3f getPosition() { return transform.getPosition(); }
    public void setPosition(float x, float y, float z) { transform.setPosition(x, y, z); }

    public Quaternionf getRotation() { return transform.getRotation(); }
    public void setRotation(Quaternionf q) { transform.setRotation(q); }

    public float getFovYDegrees() { return fovYDegrees; }
    public float getAspect() { return aspect; }
    public float getNear() { return near; }
    public float getFar() { return far; }
}
