package com.jvn.core.vn.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.jvn.core.vn.VnScene;

/**
 * Dynamically compiles and executes Java snippets inside VNS scripts.
 * Supports:
 * <ul>
 *   <li>Inline execution blocks with Vn.* facade binding</li>
 *   <li>Custom imports via {@code @jimport}</li>
 *   <li>Variable bridge via {@code @bind}</li>
 *   <li>Init blocks (compile-time utility classes)</li>
 *   <li>User-defined classes via {@code [java class Name]}</li>
 *   <li>Error line-number remapping to source script lines</li>
 *   <li>Shared context via per-scenario class registry</li>
 * </ul>
 */
public class InMemoryJavaCompiler {

  private static final Map<String, Map<String, Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();

  // Per-class preamble line counts for accurate line remapping at both compile and runtime
  private static final Map<String, Integer> PREAMBLE_LINES = new ConcurrentHashMap<>();

  /**
   * Execute an inline Java block with full context support.
   *
   * @param payload    encoded payload (may contain metadata headers)
   * @param scene      the current VnScene
   */
  public static void execute(String payload, VnScene scene) throws Exception {
    ExecutionContext ctx = ExecutionContext.parse(payload);
    String cacheKey = md5(ctx.code + ctx.imports.toString() + ctx.binds.toString());
    String className = "InlineJavaBlock_" + cacheKey;
    String scope = ctx.scenarioId != null ? ctx.scenarioId : "_global_";
    Map<String, Class<?>> scopeCache = CLASS_CACHE.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
    Class<?> clazz = scopeCache.get(className);
    int preamble = computeInlinePreambleLines(ctx);

    if (clazz == null) {
      String source = generateInlineSource(className, ctx);
      clazz = compileClass(className, source, ctx.scenarioId, ctx.sourceLine, ctx.sourceName, preamble);
      scopeCache.put(className, clazz);
    }

    // Bind Vn facade and execute
    String fqn = "com.jvn.core.vn.dynamic." + className;
    Vn.bind(scene);
    try {
      clazz.getMethod("execute", VnScene.class).invoke(null, scene);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
      int remappedLine = remapRuntimeLine(cause, fqn, preamble, ctx.sourceLine);
      throw new JavaRuntimeException(cause, ctx.sourceName, remappedLine);
    } finally {
      Vn.unbind();
    }
  }

  /**
   * Compile an init-time utility class (persists in the scenario context).
   */
  public static void compileInitClass(String className, String userCode,
                                       List<String> imports, String scenarioId,
                                       int sourceLine, String sourceName) throws Exception {
    String fullClassName = "VnInit_" + sanitize(className);
    String cacheKey = md5(fullClassName + userCode);
    String scope = scenarioId != null ? scenarioId : "_global_";
    Map<String, Class<?>> scopeCache = CLASS_CACHE.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
    if (scopeCache.containsKey(cacheKey)) return; // already compiled

    String source = generateInitSource(fullClassName, userCode, imports);
    int preamble = 3 + imports.size() + 1; // package + default imports + user imports + class decl
    Class<?> clazz = compileClass(fullClassName, source, scenarioId, sourceLine, sourceName, preamble);
    scopeCache.put(cacheKey, clazz);

    // Register in context loader for this scenario
    registerContextClass(scenarioId, fullClassName, clazz);
  }

  /**
   * Compile a user-defined class (available to subsequent blocks in same scenario).
   */
  public static void compileUserClass(String className, String userCode,
                                       List<String> imports, String scenarioId,
                                       int sourceLine, String sourceName) throws Exception {
    String fullClassName = sanitize(className);
    String cacheKey = md5(fullClassName + userCode);
    String scope = scenarioId != null ? scenarioId : "_global_";
    Map<String, Class<?>> scopeCache = CLASS_CACHE.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
    if (scopeCache.containsKey(cacheKey)) return;

    String source = generateUserClassSource(fullClassName, userCode, imports);
    int preamble = 3 + imports.size() + 1; // package + default imports + user imports + class decl
    Class<?> clazz = compileClass(fullClassName, source, scenarioId, sourceLine, sourceName, preamble);
    scopeCache.put(cacheKey, clazz);
    registerContextClass(scenarioId, fullClassName, clazz);
  }

  /**
   * Execute an init class's static initializer (if it has an init() method).
   */
  public static void executeInit(String className, String userCode,
                                  List<String> imports, String scenarioId,
                                  int sourceLine, String sourceName, VnScene scene) throws Exception {
    String fullClassName = "VnInit_" + sanitize(className);
    String cacheKey = md5(fullClassName + userCode);
    String scope = scenarioId != null ? scenarioId : "_global_";
    Map<String, Class<?>> scopeCache = CLASS_CACHE.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
    Class<?> clazz = scopeCache.get(cacheKey);

    if (clazz == null) {
      compileInitClass(className, userCode, imports, scenarioId, sourceLine, sourceName);
      clazz = scopeCache.get(cacheKey);
    }

    if (clazz != null) {
      try {
        var initMethod = clazz.getMethod("init", VnScene.class);
        Vn.bind(scene);
        try {
          initMethod.invoke(null, scene);
        } finally {
          Vn.unbind();
        }
      } catch (NoSuchMethodException ignored) {
        // No init() method — that's fine, class just defines utilities
      }
    }
  }

  // ─── Source Generation ─────────────────────────────────────────────

  private static String generateInlineSource(String className, ExecutionContext ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("package com.jvn.core.vn.dynamic;\n");
    sb.append("import com.jvn.core.vn.*;\n");
    sb.append("import com.jvn.core.vn.script.Vn;\n");
    for (String imp : ctx.imports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("public class ").append(className).append(" {\n");
    sb.append("  public static void execute(VnScene scene) throws Exception {\n");
    sb.append("    VnState state = scene.getState();\n");

    // Variable bridge preamble — read bound vars from state
    for (BindDecl bind : ctx.binds) {
      sb.append("    ").append(bind.generateRead()).append("\n");
    }

    // User code
    sb.append(ctx.code).append("\n");

    // Variable bridge epilogue — write bound vars back to state
    for (BindDecl bind : ctx.binds) {
      sb.append("    ").append(bind.generateWrite()).append("\n");
    }

    sb.append("  }\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static String generateInitSource(String className, String userCode, List<String> imports) {
    StringBuilder sb = new StringBuilder();
    sb.append("package com.jvn.core.vn.dynamic;\n");
    sb.append("import com.jvn.core.vn.*;\n");
    sb.append("import com.jvn.core.vn.script.Vn;\n");
    for (String imp : imports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("public class ").append(className).append(" {\n");
    sb.append(userCode).append("\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static String generateUserClassSource(String className, String userCode, List<String> imports) {
    StringBuilder sb = new StringBuilder();
    sb.append("package com.jvn.core.vn.dynamic;\n");
    sb.append("import com.jvn.core.vn.*;\n");
    sb.append("import com.jvn.core.vn.script.Vn;\n");
    for (String imp : imports) {
      sb.append("import ").append(imp).append(";\n");
    }
    sb.append("public class ").append(className).append(" {\n");
    sb.append(userCode).append("\n");
    sb.append("}\n");
    return sb.toString();
  }

  // ─── Compilation ──────────────────────────────────────────────────

  /**
   * Compute the number of generated preamble lines before user code in an inline block.
   * This must stay in sync with {@link #generateInlineSource}.
   */
  public static int computeInlinePreambleLines(ExecutionContext ctx) {
    // package + 2 default imports + user imports + class decl + method decl + state decl + bind reads
    return 6 + ctx.imports.size() + ctx.binds.size();
  }

  private static Class<?> compileClass(String className, String sourceCode,
                                        String scenarioId, int scriptSourceLine,
                                        String sourceName, int preambleLines) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No JavaCompiler available. Ensure you are running on a JDK, not a JRE.");
    }

    ClassLoader parentLoader = resolveParentLoader(scenarioId);
    MemoryJavaFileManager fileManager = new MemoryJavaFileManager(
        compiler.getStandardFileManager(null, null, null), parentLoader, contextClassBytes(scenarioId));
    JavaFileObject sourceFile = new StringJavaFileObject("com.jvn.core.vn.dynamic." + className, sourceCode);

    List<String> options = List.of("-classpath", buildClasspath());
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaCompiler.CompilationTask task = compiler.getTask(
        null, fileManager, diagnostics, options, null, Collections.singletonList(sourceFile));
    boolean success = task.call();

    if (!success) {
      StringBuilder errorMsg = new StringBuilder("Inline Java compilation failed:\n");
      int primaryLine = -1;
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
        long line = diagnostic.getLineNumber();
        // Remap line numbers: subtract preamble, add script source offset
        long remapped = line - preambleLines + scriptSourceLine;
        if (remapped < scriptSourceLine) remapped = line; // fallback
        if (primaryLine < 0 && diagnostic.getKind() == Diagnostic.Kind.ERROR) {
          primaryLine = (int) remapped;
        }
        errorMsg.append("  line ").append(remapped).append(": ")
                .append(diagnostic.getMessage(null)).append("\n");
      }
      throw new JavaCompilationException(errorMsg.toString(), sourceName, primaryLine > 0 ? primaryLine : scriptSourceLine);
    }

    // Store preamble for runtime line remapping
    String fqn = "com.jvn.core.vn.dynamic." + className;
    PREAMBLE_LINES.put(fqn, preambleLines);

    registerContextClassBytes(scenarioId, fileManager.compiledBytes());
    return fileManager.getClassLoader(null).loadClass(fqn);
  }

  /**
   * Remap a runtime exception's stack trace line back to the VNS source line.
   * Scans the exception's stack for a frame in the generated class and adjusts.
   */
  public static int remapRuntimeLine(Throwable cause, String generatedClassName, int preambleLines, int scriptSourceLine) {
    if (cause == null) return scriptSourceLine;
    for (StackTraceElement frame : cause.getStackTrace()) {
      if (generatedClassName.equals(frame.getClassName()) && frame.getLineNumber() > 0) {
        long remapped = frame.getLineNumber() - preambleLines + scriptSourceLine;
        return remapped >= scriptSourceLine ? (int) remapped : frame.getLineNumber();
      }
    }
    return scriptSourceLine;
  }

  // ─── Context Registry (shared classes per scenario) ────────────────

  private static final Map<String, Map<String, Class<?>>> SCENARIO_CLASSES = new ConcurrentHashMap<>();
  private static final Map<String, Map<String, byte[]>> SCENARIO_CLASS_BYTES = new ConcurrentHashMap<>();

  private static void registerContextClass(String scenarioId, String className, Class<?> clazz) {
    SCENARIO_CLASSES.computeIfAbsent(scenarioId != null ? scenarioId : "_global_",
        k -> new ConcurrentHashMap<>()).put(className, clazz);
  }

  private static void registerContextClassBytes(String scenarioId, Map<String, byte[]> classBytes) {
    if (classBytes == null || classBytes.isEmpty()) return;
    SCENARIO_CLASS_BYTES.computeIfAbsent(scenarioId != null ? scenarioId : "_global_",
        k -> new ConcurrentHashMap<>()).putAll(classBytes);
  }

  private static Map<String, byte[]> contextClassBytes(String scenarioId) {
    Map<String, byte[]> classBytes = SCENARIO_CLASS_BYTES.get(scenarioId != null ? scenarioId : "_global_");
    return classBytes == null ? Map.of() : classBytes;
  }

  private static ClassLoader resolveParentLoader(String scenarioId) {
    Map<String, Class<?>> classes = scenarioId != null ? SCENARIO_CLASSES.get(scenarioId) : null;
    if (classes == null || classes.isEmpty()) {
      return InMemoryJavaCompiler.class.getClassLoader();
    }
    // Build a classloader that can find scenario-scoped classes
    return new SecureClassLoader(InMemoryJavaCompiler.class.getClassLoader()) {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        String simple = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        Class<?> c = classes.get(simple);
        if (c != null) return c;
        // Also check with full dynamic package name
        String stripped = name.replace("com.jvn.core.vn.dynamic.", "");
        c = classes.get(stripped);
        if (c != null) return c;
        return super.findClass(name);
      }
    };
  }

  public static void clearScenarioContext(String scenarioId) {
    if (scenarioId != null) {
      SCENARIO_CLASSES.remove(scenarioId);
      SCENARIO_CLASS_BYTES.remove(scenarioId);
      CLASS_CACHE.remove(scenarioId);
    }
  }

  // ─── Payload Protocol ─────────────────────────────────────────────

  /**
   * Encoded payload format for inline_java external commands:
   * <pre>
   * §IMPORTS§com.foo.Bar|com.baz.Qux
   * §BINDS§int:hp|String:name
   * §LINE§42
   * §SCENARIO§my_scenario
   * §CODE§
   * actual user code here...
   * </pre>
   */
  public static class ExecutionContext {
    public List<String> imports = new ArrayList<>();
    public List<BindDecl> binds = new ArrayList<>();
    public int sourceLine = 0;
    public String scenarioId = null;
    public String sourceName = null;
    public String code = "";

    public static ExecutionContext parse(String payload) {
      ExecutionContext ctx = new ExecutionContext();
      if (payload == null || payload.isEmpty()) return ctx;

      if (!payload.contains("§CODE§")) {
        // Legacy format: raw code only
        ctx.code = payload;
        return ctx;
      }

      String[] parts = payload.split("§CODE§", 2);
      ctx.code = parts.length > 1 ? parts[1] : "";

      String header = parts[0];
      for (String line : header.split("\n")) {
        line = line.trim();
        if (line.startsWith("§IMPORTS§")) {
          String val = line.substring("§IMPORTS§".length());
          if (!val.isEmpty()) {
            for (String imp : val.split("\\|")) {
              if (!imp.isBlank()) ctx.imports.add(imp.trim());
            }
          }
        } else if (line.startsWith("§BINDS§")) {
          String val = line.substring("§BINDS§".length());
          if (!val.isEmpty()) {
            for (String b : val.split("\\|")) {
              if (!b.isBlank()) ctx.binds.add(BindDecl.parse(b.trim()));
            }
          }
        } else if (line.startsWith("§LINE§")) {
          try { ctx.sourceLine = Integer.parseInt(line.substring("§LINE§".length()).trim()); }
          catch (NumberFormatException ignored) {}
        } else if (line.startsWith("§SCENARIO§")) {
          ctx.scenarioId = line.substring("§SCENARIO§".length()).trim();
        } else if (line.startsWith("§SOURCE§")) {
          ctx.sourceName = line.substring("§SOURCE§".length()).trim();
        }
      }
      return ctx;
    }
  }

  /**
   * Exception carrying structured runtime error info for the error overlay.
   * Wraps the original cause with remapped source location.
   */
  public static class JavaRuntimeException extends RuntimeException {
    private final String sourceName;
    private final int lineNumber;

    public JavaRuntimeException(Throwable cause, String sourceName, int lineNumber) {
      super(cause.getMessage(), cause);
      this.sourceName = sourceName;
      this.lineNumber = lineNumber;
    }

    public String getSourceName() { return sourceName; }
    public int getLineNumber() { return lineNumber; }
  }

  /**
   * Exception carrying structured compilation diagnostics for the error overlay.
   */
  public static class JavaCompilationException extends RuntimeException {
    private final String sourceName;
    private final int lineNumber;

    public JavaCompilationException(String message, String sourceName, int lineNumber) {
      super(message);
      this.sourceName = sourceName;
      this.lineNumber = lineNumber;
    }

    public String getSourceName() { return sourceName; }
    public int getLineNumber() { return lineNumber; }
  }

  /**
   * Variable binding declaration: type + name.
   * Generates code to read from / write back to VnState.
   */
  public static class BindDecl {
    String type;
    String name;

    BindDecl(String type, String name) {
      this.type = type;
      this.name = name;
    }

    public static BindDecl parse(String token) {
      int colon = token.indexOf(':');
      if (colon < 0) return new BindDecl("Object", token);
      return new BindDecl(token.substring(0, colon), token.substring(colon + 1));
    }

    String generateRead() {
      return switch (type) {
        case "int" -> type + " " + name + " = state.getVariable(\"" + name + "\") instanceof Number _n ? _n.intValue() : 0;";
        case "long" -> type + " " + name + " = state.getVariable(\"" + name + "\") instanceof Number _n ? _n.longValue() : 0L;";
        case "double" -> type + " " + name + " = state.getVariable(\"" + name + "\") instanceof Number _n ? _n.doubleValue() : 0.0;";
        case "float" -> type + " " + name + " = state.getVariable(\"" + name + "\") instanceof Number _n ? _n.floatValue() : 0.0f;";
        case "boolean" -> type + " " + name + " = state.getVariable(\"" + name + "\") instanceof Boolean _b ? _b : false;";
        case "String" -> type + " " + name + " = state.getVariable(\"" + name + "\") != null ? state.getVariable(\"" + name + "\").toString() : \"\";";
        default -> type + " " + name + " = (" + type + ") state.getVariable(\"" + name + "\");";
      };
    }

    String generateWrite() {
      return "state.setVariable(\"" + name + "\", " + name + ");";
    }
  }

  // ─── Utility ──────────────────────────────────────────────────────

  private static String buildClasspath() {
    StringBuilder cp = new StringBuilder();
    ClassLoader cl = InMemoryJavaCompiler.class.getClassLoader();
    if (cl instanceof URLClassLoader ucl) {
      for (URL url : ucl.getURLs()) {
        if (cp.length() > 0) cp.append(System.getProperty("path.separator"));
        cp.append(url.getPath());
      }
    }
    if (cp.length() == 0) {
      cp.append(System.getProperty("java.class.path", ""));
    }
    return cp.toString();
  }

  private static String sanitize(String name) {
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static String md5(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hash = md.digest(input.getBytes());
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      return String.valueOf(Math.abs(input.hashCode()));
    }
  }

  // ─── In-Memory File Objects ───────────────────────────────────────

  static class StringJavaFileObject extends SimpleJavaFileObject {
    private final String code;

    protected StringJavaFileObject(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

  static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, MemoryClassFileObject> classFiles = new ConcurrentHashMap<>();
    private final Map<String, byte[]> contextClasses;
    private final ClassLoader parentLoader;

    protected MemoryJavaFileManager(JavaFileManager fileManager,
                                    ClassLoader parentLoader,
                                    Map<String, byte[]> contextClasses) {
      super(fileManager);
      this.parentLoader = parentLoader;
      this.contextClasses = contextClasses == null ? Map.of() : new HashMap<>(contextClasses);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
      MemoryClassFileObject classFileObject = new MemoryClassFileObject(className, kind);
      classFiles.put(className, classFileObject);
      return classFileObject;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location,
                                             String className,
                                             JavaFileObject.Kind kind) throws java.io.IOException {
      if (kind == JavaFileObject.Kind.CLASS) {
        byte[] bytes = contextClasses.get(className);
        if (bytes != null) return new ByteArrayJavaFileObject(className, bytes);
      }
      return super.getJavaFileForInput(location, className, kind);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         java.util.Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws java.io.IOException {
      Iterable<JavaFileObject> base = super.list(location, packageName, kinds, recurse);
      if (!kinds.contains(JavaFileObject.Kind.CLASS) || contextClasses.isEmpty()) return base;
      List<JavaFileObject> combined = new ArrayList<>();
      for (JavaFileObject file : base) combined.add(file);
      String prefix = packageName == null || packageName.isBlank() ? "" : packageName + ".";
      for (Map.Entry<String, byte[]> entry : contextClasses.entrySet()) {
        String binaryName = entry.getKey();
        if (binaryName.startsWith(prefix) && binaryName.indexOf('.', prefix.length()) < 0) {
          combined.add(new ByteArrayJavaFileObject(binaryName, entry.getValue()));
        }
      }
      return combined;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
      if (file instanceof ByteArrayJavaFileObject byteFile) return byteFile.binaryName();
      return super.inferBinaryName(location, file);
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
      return new SecureClassLoader(parentLoader) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          MemoryClassFileObject file = classFiles.get(name);
          if (file != null) {
            byte[] bytes = file.getBytes();
            return super.defineClass(name, bytes, 0, bytes.length);
          }
          byte[] bytes = contextClasses.get(name);
          if (bytes != null) {
            return super.defineClass(name, bytes, 0, bytes.length);
          }
          return super.findClass(name);
        }
      };
    }

    Map<String, byte[]> compiledBytes() {
      Map<String, byte[]> out = new HashMap<>();
      for (Map.Entry<String, MemoryClassFileObject> entry : classFiles.entrySet()) {
        out.put(entry.getKey(), entry.getValue().getBytes());
      }
      return out;
    }
  }

  static class ByteArrayJavaFileObject extends SimpleJavaFileObject {
    private final String binaryName;
    private final byte[] bytes;

    ByteArrayJavaFileObject(String binaryName, byte[] bytes) {
      super(URI.create("mem:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
      this.binaryName = binaryName;
      this.bytes = bytes == null ? new byte[0] : bytes;
    }

    String binaryName() {
      return binaryName;
    }

    @Override
    public InputStream openInputStream() {
      return new ByteArrayInputStream(bytes);
    }
  }

  static class MemoryClassFileObject extends SimpleJavaFileObject {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final String className;

    protected MemoryClassFileObject(String className, Kind kind) {
      super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
      this.className = className;
    }

    @Override
    public OutputStream openOutputStream() {
      return outputStream;
    }

    public byte[] getBytes() {
      return outputStream.toByteArray();
    }

    @Override
    public String getName() {
      return className;
    }
  }
}
