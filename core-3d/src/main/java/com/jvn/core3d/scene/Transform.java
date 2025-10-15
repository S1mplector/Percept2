package com.jvn.core3d.scene;

import com.jvn.core3d.math.Matrix4f;
import com.jvn.core3d.math.Quaternionf;
import com.jvn.core3d.math.Vector3f;

public class Transform {
    private Vector3f position = new Vector3f(0, 0, 0);
    private Quaternionf rotation = new Quaternionf(0, 0, 0, 1);
    private Vector3f scale = new Vector3f(1, 1, 1);

    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f p) { this.position = new Vector3f(p); }
    public void setPosition(float x, float y, float z) { this.position.set(x, y, z); }

    public Quaternionf getRotation() { return rotation; }
    public void setRotation(Quaternionf r) { this.rotation = new Quaternionf(r); }

    public Vector3f getScale() { return scale; }
    public void setScale(Vector3f s) { this.scale = new Vector3f(s); }
    public void setScale(float x, float y, float z) { this.scale.set(x, y, z); }

    public Matrix4f toMatrix() {
        return Matrix4f.compose(position, rotation, scale);
    }

    public Vector3f forward() { return rotation.transform(new Vector3f(0, 0, -1)).normalize(); }
    public Vector3f up() { return rotation.transform(new Vector3f(0, 1, 0)).normalize(); }
    public Vector3f right() { return rotation.transform(new Vector3f(1, 0, 0)).normalize(); }
}
