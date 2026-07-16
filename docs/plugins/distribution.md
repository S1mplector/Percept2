# Packaging and distribution

A distributable plugin is one JAR containing compiled plugin classes, `jvn-plugin.json`, and any shaded third-party dependencies—but no bundled copy of `plugin-api`.

```bash
jar tf build/libs/my-plugin-1.0.0.jar
```

## Versioning

Use semantic versioning for the plugin and declare the narrowest honest `jvnApi` range. Increment major for removed registrations or incompatible configuration, minor for compatible capabilities, and patch for behavior-preserving fixes.

## Release checklist

1. Run unit and host integration tests on Java 21.
2. Test editor and runtime loading independently.
3. Verify behavior with no project and paths containing spaces.
4. Inspect the JAR for accidental API or secret inclusion.
5. Publish checksums and release notes.
6. Document data migrations and newly requested capabilities.

There is no central JVN marketplace yet. Distribute signed releases through a trusted repository and give users the exact installation scope and checksum.
