package com.jvn.core.vn.script;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureClassLoader;
import java.util.Collections;
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
 */
public class InMemoryJavaCompiler {

  private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
  
  public static void execute(String javaCode, VnScene scene) throws Exception {
    String hash = md5(javaCode);
    String className = "InlineJavaBlock_" + hash;
    Class<?> clazz = CLASS_CACHE.get(className);
    
    if (clazz == null) {
      clazz = compileClass(className, javaCode);
      CLASS_CACHE.put(className, clazz);
    }
    
    // Execute
    clazz.getMethod("execute", VnScene.class).invoke(null, scene);
  }

  private static Class<?> compileClass(String className, String userCode) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No JavaCompiler available. Ensure you are running on a JDK, not a JRE.");
    }

    String sourceCode = "package com.jvn.core.vn.dynamic;\n" +
      "import com.jvn.core.vn.*;\n" +
      "public class " + className + " {\n" +
      "  public static void execute(VnScene scene) throws Exception {\n" +
      "    VnState state = scene.getState();\n" +
      userCode + "\n" +
      "  }\n" +
      "}\n";

    MemoryJavaFileManager fileManager = new MemoryJavaFileManager(compiler.getStandardFileManager(null, null, null));
    JavaFileObject sourceFile = new StringJavaFileObject("com.jvn.core.vn.dynamic." + className, sourceCode);

    List<String> options = List.of("-classpath", buildClasspath());
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, Collections.singletonList(sourceFile));
    boolean success = task.call();
    
    if (!success) {
      StringBuilder errorMsg = new StringBuilder("Failed to compile inline Java block:\n");
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
        errorMsg.append(diagnostic.getMessage(null)).append(" at line ").append(diagnostic.getLineNumber()).append("\n");
      }
      throw new RuntimeException(errorMsg.toString());
    }

    return fileManager.getClassLoader(null).loadClass("com.jvn.core.vn.dynamic." + className);
  }

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

    protected MemoryJavaFileManager(JavaFileManager fileManager) {
      super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
      MemoryClassFileObject classFileObject = new MemoryClassFileObject(className, kind);
      classFiles.put(className, classFileObject);
      return classFileObject;
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
      return new SecureClassLoader(InMemoryJavaCompiler.class.getClassLoader()) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          MemoryClassFileObject file = classFiles.get(name);
          if (file != null) {
            byte[] bytes = file.getBytes();
            return super.defineClass(name, bytes, 0, bytes.length);
          }
          return super.findClass(name);
        }
      };
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
