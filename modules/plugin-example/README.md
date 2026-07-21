# JVN example plugin

Reference plugin demonstrating script commands, editor tools, runtime listeners, and a named easing extension without accessing engine or editor internals.

```bash
./gradlew :plugin-example:jar
mkdir -p ~/.jvn/plugins
cp modules/plugin-example/build/libs/jvn-example-plugin-*.jar ~/.jvn/plugins/
```

After restarting JVN, run the editor action under **Tools → Plugins**, or call the command from VNS:

```vns
[plugin hello.greet Ada]
```

The plugin also contributes `hello.elastic-pop`, available in Puppeteer's easing catalog and timeline source:

```jes
easing: "hello.elastic-pop(overshoot: 1.4, settle: 0.8)"
```

See [Plugin authoring](../../docs/plugins/authoring.md) for a guided implementation.
