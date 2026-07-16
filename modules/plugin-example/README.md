# JVN example plugin

Reference plugin demonstrating script commands, editor tools, and runtime listeners without accessing engine or editor internals.

```bash
./gradlew :plugin-example:jar
mkdir -p ~/.jvn/plugins
cp modules/plugin-example/build/libs/jvn-example-plugin-*.jar ~/.jvn/plugins/
```

After restarting JVN, run the editor action under **Tools → Plugins**, or call the command from VNS:

```vns
[plugin hello.greet Ada]
```

See [Plugin authoring](../../docs/plugins/authoring.md) for a guided implementation.
