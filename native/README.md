# JVN Native Bridge

This folder contains the JNI entry point for optional C++ acceleration.

## Build (macOS)

```
export JAVA_HOME=$(/usr/libexec/java_home)
clang++ -std=c++17 -shared -fPIC \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
  -o native/libjvn_math.dylib native/jvn_math.cpp
```

Or:
```
./native/build.sh
```

Or (macOS only):
```
./native/build_mac.sh
```

## Build (Linux)

```
export JAVA_HOME=/path/to/jdk
g++ -std=c++17 -shared -fPIC \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -o native/libjvn_math.so native/jvn_math.cpp
```

Or:
```
./native/build.sh
```

Or (Linux only):
```
./native/build_linux.sh
```
## Build (Windows, MSVC)

```
set JAVA_HOME=C:\Path\To\JDK
cl /EHsc /LD native\jvn_math.cpp ^
  /I "%JAVA_HOME%\include" /I "%JAVA_HOME%\include\win32" ^
  /Fe:native\jvn_math.dll
```

Or:
```
powershell -ExecutionPolicy Bypass -File native/build.ps1
```

## Usage

- Java loads `jvn_math` via `NativeLibraryLoader`.
- Place the built library in `native/` (or `native/<os>/`) and set `-Djvn.native.path=...` if needed.
- Use `NativeMathBridge.dotProduct(...)` for a safe fallback when the library is missing.
- Use `NativeMathBridge.matMul(...)` for matrix multiply (row-major arrays).
