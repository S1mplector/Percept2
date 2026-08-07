package com.jvn.testkit.jmh;

import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.Keyframe;
import com.jvn.editor.ui.actioneditor.PropertyType;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Measures the keyframe lookup performed for each animated Puppeteer property per preview frame. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class PuppeteerTrackBench {
    private static final PropertyType[] PREVIEW_PROPERTIES = {
        PropertyType.X,
        PropertyType.Y,
        PropertyType.Z,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.MIRROR_X,
        PropertyType.ALPHA,
        PropertyType.VISIBILITY,
        PropertyType.BLUR
    };

    @Param({"16", "256", "4096"})
    public int keyframeCount;

    private EntityTrack track;
    private AnimationProject project;
    private double[] sampleTimes;
    private int sampleCursor;
    private double playbackTime;

    @Setup
    public void setup() {
        track = new EntityTrack("bench-sprite");
        for (int propertyIndex = 0; propertyIndex < PREVIEW_PROPERTIES.length; propertyIndex++) {
            List<Keyframe> keyframes = new ArrayList<>(keyframeCount);
            for (int i = 0; i < keyframeCount; i++) {
                keyframes.add(new Keyframe(i * 10.0, propertyIndex * 1000.0 + i));
            }
            track.setKeyframes(PREVIEW_PROPERTIES[propertyIndex], keyframes);
        }
        project = new AnimationProject();
        project.addTrack(track);

        sampleTimes = new double[1024];
        long state = 0x9e3779b97f4a7c15L;
        for (int i = 0; i < sampleTimes.length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            sampleTimes[i] = Math.floorMod(state >>> 16, keyframeCount * 1000L) / 100.0;
        }
    }

    @Benchmark
    public double sampleProperty() {
        double time = sampleTimes[sampleCursor++ & (sampleTimes.length - 1)];
        return track.getValueAt(PropertyType.X, time);
    }

    @Benchmark
    public void samplePreviewFrame(Blackhole blackhole) {
        double time = sampleTimes[sampleCursor++ & (sampleTimes.length - 1)];
        for (PropertyType property : PREVIEW_PROPERTIES) {
            blackhole.consume(track.getValueAt(property, time));
        }
    }

    @Benchmark
    public AnimationProject.EffectiveEntityTransform sampleEffectiveEntityTransform() {
        double time = sampleTimes[sampleCursor++ & (sampleTimes.length - 1)];
        return project.computeEffectiveEntityTransform("bench-sprite", time);
    }

    /** Mirrors normal preview playback, where time moves forward one frame at a time. */
    @Benchmark
    public AnimationProject.EffectiveEntityTransform sampleEffectiveEntityTransformPlayback() {
        double time = playbackTime;
        playbackTime += 1000.0 / 60.0;
        if (playbackTime >= keyframeCount * 10.0) playbackTime = 0.0;
        return project.computeEffectiveEntityTransform("bench-sprite", time);
    }
}
