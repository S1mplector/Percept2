# Simjot Profiler - CPU & RAM Usage Analyzer

A native C-based CLI tool for measuring CPU and RAM usage of Simjot or any process.

## Features

- **Real-time monitoring** - Tracks RSS memory, virtual memory, and CPU usage
- **Per-process profiling** - Profile any running process by PID
- **High usage detection** - Warns when memory >50MB or CPU >15%
- **Native accuracy** - Uses macOS Mach APIs / Linux procfs for precise measurements
- **CLI-focused output** - Formatted tables and summaries for terminal use

## Building

```bash
cd Simjot/src/main/native
chmod +x build_profiler.sh
./build_profiler.sh
```

Or manually:

```bash
cd Simjot/src/main/native
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --target profiler_cli
```

The executable will be at `build/simjot_profiler`.

## Usage

### Profile a Simjot Process

First, find the Simjot PID:
```bash
pgrep -f Simjot
# or
ps aux | grep java | grep Simjot
```

Then profile it:
```bash
./simjot_profiler -p <pid>
```

### Options

| Option | Description |
|--------|-------------|
| `-p, --pid <pid>` | Profile a running process by PID |
| `-i, --interval <ms>` | Sampling interval in milliseconds (default: 500) |
| `-d, --duration <s>` | Duration to profile in seconds (default: 10) |
| `-c, --continuous` | Run continuously until Ctrl+C |
| `-q, --quiet` | Only show status line (no table) |
| `-s, --self` | Profile the profiler itself (for testing) |
| `-h, --help` | Show help |

### Examples

```bash
# Profile process 12345 for 10 seconds
./simjot_profiler -p 12345

# Continuous profiling until Ctrl+C
./simjot_profiler -p 12345 -c

# Sample every 100ms for 60 seconds
./simjot_profiler -p 12345 -i 100 -d 60

# Quiet mode (single status line)
./simjot_profiler -p 12345 -c -q

# Self-test the profiler
./simjot_profiler -s
```

## Output Example

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                         SIMJOT PROCESS PROFILER                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ PID: 12345      │  Interval: 500ms  │  CPUs: 8                              
╠══════════════════════════════════════════════════════════════════════════════╣
║  TIME   │    RSS     │   VMEM     │  CPU%  │  STATUS                        
╠══════════════════════════════════════════════════════════════════════════════╣
║   0.5s │   128.5 MB │     2.1 GB │  12.3% │ 
║   1.0s │   130.2 MB │     2.1 GB │   8.5% │ 
║   1.5s │   132.8 MB │     2.2 GB │  18.2% │ ⚠️  HIGH CPU
╠══════════════════════════════════════════════════════════════════════════════╣
║                                  SUMMARY                                     ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Duration:     10.0 seconds                                                   
║ Samples:      20                                                             
║ Peak RSS:     145.3 MB                                                       
║ Avg RSS:      135.2 MB                                                       
║ Peak CPU:     18.2%                                                          
║ Avg CPU:      10.5%                                                          
║ System RAM:   12.5 GB / 16.0 GB                                              
╚══════════════════════════════════════════════════════════════════════════════╝

⚠️  HIGH CPU USAGE detected (peak: 18.2%)
    Consider profiling CPU-intensive code paths.
```

## Interpreting Results

### Memory (RSS - Resident Set Size)
- **< 100 MB**: Normal for basic operations
- **100-200 MB**: Normal with multiple entries/images loaded
- **200-500 MB**: High - check for memory leaks or large caches
- **> 500 MB**: Very high - investigate memory usage

### CPU Usage
- **< 5%**: Idle/minimal activity
- **5-15%**: Normal active usage
- **15-30%**: High - check for expensive operations
- **> 30%**: Very high - likely running intensive task

### High Usage Warnings
The profiler automatically flags:
- **⚠️ HIGH MEM**: RSS > 50MB
- **⚠️ HIGH CPU**: CPU > 15%

## Integration with Simjot

### From Java Code

The `ComponentProfiler` class in `main.infrastructure.monitoring` provides Java bindings to the native profiler (when the native library is available):

```java
// Register component for profiling
ComponentProfiler profiler = ComponentProfiler.getInstance();
profiler.init(500); // 500ms interval
profiler.registerComponent("UI/Drawing");

// In component thread
profiler.registerThread("UI/Drawing");
// ... do work ...
profiler.unregisterThread("UI/Drawing");

// Track allocations
profiler.trackAllocation("UI/Drawing", imageBytes.length);

// Start profiling
profiler.start();

// Print report
profiler.printReport();
```

## Files Created

| File | Description |
|------|-------------|
| `src/main/native/src/profiler/profiler.c` | Native component profiler library |
| `src/main/native/tests/profiler_cli.c` | Standalone CLI profiler |
| `src/main/native/build_profiler.sh` | Build script |
| `src/main/native/include/simjot_native.h` | Header with profiler declarations |
| `src/main/infrastructure/monitoring/ComponentProfiler.java` | Java bindings |

## Troubleshooting

### "Process does not exist or is not accessible"
- Ensure the PID is correct
- On macOS, may need to run from same user or with elevated permissions

### Build fails
- Ensure CMake 3.20+ is installed
- On macOS, ensure Xcode command line tools are installed

### No output
- Verify process is running: `ps -p <pid>`
- Try with `-s` flag to self-test

## License

Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
