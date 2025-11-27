# Tomasulo Algorithm Simulator (JavaFX)

This repository contains a JavaFX-based Tomasulo algorithm simulator for the microprocessors course project. The simulator demonstrates cycle-by-cycle execution of MIPS-like instructions with:

- **Reservation stations** for Add/Sub, Mul/Div, and Integer operations
- **Load/Store buffers** for memory operations
- **Register file** with register renaming (tags)
- **Data cache** with configurable size, block size, hit latency, and miss penalty
- **Instruction queue** showing pending instructions
- **Cycle-by-cycle logging** of issue, execute, and writeback stages

## Features

✅ Handles RAW, WAR, and WAW hazards via register renaming  
✅ Supports FP operations (ADD.D, SUB.D, MUL.D, DIV.D)  
✅ Supports integer operations (ADDI, SUBI, DADDI, DSUBI)  
✅ Supports load/store (L.D, S.D, LW, SW, etc.)  
✅ Supports branches (BEQ, BNE)  
✅ Configurable latencies for all instruction types  
✅ Direct-mapped data cache with configurable parameters  
✅ GUI displays all reservation stations, registers, and logs in real-time  

## Build & Run

### Prerequisites
- **Java 11 or higher**
- **Apache Maven 3.6+**

### Running the Simulator

From the repository root (Windows PowerShell):

```powershell
mvn clean javafx:run
```

This compiles the code and launches the JavaFX GUI.

### Alternative: Package as JAR

To create a standalone JAR:

```powershell
mvn clean package
```

Then run:

```powershell
java -jar target/tomasulo-simulator-1.0-SNAPSHOT.jar
```

## Usage Guide

### 1. Load Instructions

Click **Load File** and select a test case (e.g., `src/main/resources/testcases.txt`).

Supported instruction formats:
- **Loads**: `L.D F6, 0(R2)`, `LW R1, 100(R2)`
- **Stores**: `S.D F6, 8(R2)`, `SW R1, 0(R2)`
- **FP Arithmetic**: `ADD.D F0, F1, F2`, `MUL.D F4, F2, F6`, `DIV.D F10, F0, F6`
- **Integer**: `ADDI R1, R2, 10`, `DADDI R1, R1, 24`
- **Branches**: `BEQ R1, R2, 0`, `BNE R1, R2, -4`

### 2. Configure Parameters

Expand the **Configuration** panel to adjust:
- **Latencies**: Add, Mul, Div, Load (cycles)
- **Cache**: Size (bytes), Block Size (bytes), Hit Latency, Miss Penalty
- **Stations**: Number of Add, Mul, Int stations and Load buffers

Click **Apply Config** to reinitialize the engine with new settings.

### 3. Step Through Simulation

- **Step Cycle**: Advance by 1 cycle
- **Run 10**: Advance by 10 cycles
- **Reset**: Clear state and reload configuration

### 4. View Results

The GUI has tabs for:
- **Instructions**: Instruction queue (pending instructions)
- **Add/Sub Stations**: Reservation stations for FP add/sub
- **Mul/Div Stations**: Reservation stations for FP mul/div
- **Int Stations**: Reservation stations for integer ops
- **Load/Store Buffers**: Buffers for memory operations
- **Registers**: Current register values (R0-R7, F0-F7)

The bottom log shows cycle-by-cycle events (issue, execute start, writeback).

## Cache & Memory Model

### Addressing Strategy
- **Byte-addressable memory**: Each memory location holds 8 bits (1 byte).
- **Load/Store**: LW/L.D reads 4 bytes starting at the computed address (`offset + base_register`).
- **Direct-mapped cache**: 
  - Index = `(address / blockSize) % numLines`
  - Tag = `address / blockSize`
- **Cache access**:
  - **Hit**: Return data in `hitLatency` cycles
  - **Miss**: Fetch block from memory, store in cache, then access (total = `missPenalty + hitLatency`)

### Example
```
LW R1, 100(R2)
```
1. Compute address: `100 + R2`
2. Check cache: index = `(100 + R2) / blockSize % numLines`
3. If hit: access in 2 cycles (default)
4. If miss: fetch block (50 cycles penalty) + access (2 cycles)

## Project Structure

```
src/main/java/com/tomasulo/
├── MainApp.java              # JavaFX GUI
├── TomasuloEngine.java       # Core simulation engine
├── Instruction.java          # Instruction model
├── InstructionType.java      # Enum of instruction types
├── ReservationStation.java   # Station structure
├── RegisterFile.java         # Registers + tags
├── MemoryCache.java          # Direct-mapped cache
└── SimulatorConfig.java      # Configuration parameters

src/main/resources/
├── testcases.txt             # Test case 1 & 3
└── testcase2.txt             # Test case 2

pom.xml                       # Maven build configuration
README.md                     # This file
```

## Test Cases

Three test cases are provided per project requirements:

### Test Case 1: Sequential Code (RAW hazards)
```assembly
L.D F6, 0(R2)
L.D F2, 8(R2)
MUL.D F0, F2, F4
SUB.D F8, F2, F6
DIV.D F10, F0, F6
ADD.D F6, F8, F2
S.D F6, 8(R2)
```

### Test Case 2: Sequential Code (WAW hazard)
```assembly
L.D F6, 0(R2)
ADD.D F7, F1, F3
L.D F2, 20(R2)
MUL.D F0, F2, F4
SUB.D F8, F2, F6
DIV.D F10, F0, F6
S.D F10, 0(R2)
```

### Test Case 3: Loop Code
```assembly
DADDI R1, R1, 24
DADDI R2, R2, 0
L.D F0, 8(R1)
MUL.D F4, F0, F2
S.D F4, 8(R1)
DSUBI R1, R1, 8
BNE R1, R2, -4
```

## Implementation Notes

### Tomasulo Algorithm Stages

1. **Issue**: Fetch instruction from queue, allocate reservation station, perform register renaming
2. **Execute**: Wait for operands (Qj, Qk = null), then execute for `latency` cycles
3. **Writeback**: Broadcast result on CDB, update waiting stations, clear reservation station

### Handling Multiple Writebacks

When multiple stations complete execution in the same cycle, only **one** is allowed to writeback per cycle (simplified model). The simulator picks the first ready station in iteration order. This can be extended to support arbitration policies.

### Branch Handling

Branches are parsed but **not executed** (no branch prediction). The simulator assumes sequential execution for demonstration. To support branches:
- Implement PC tracking
- Flush instruction queue on branch taken
- Add branch prediction logic (optional)

### Floating-Point Values

Per project requirements, FP registers are treated as integer containers. No mantissa/exponent handling is performed.

## Known Limitations

- **Single issue/writeback per cycle**: Simplified for clarity
- **No branch execution**: Branches parsed but not taken
- **Deterministic results**: Computed values are synthetic (cycle-based)
- **Cache**: Instruction fetch not modeled (data cache only)

## Extending the Simulator

To add features:
1. **Multiple issue/writeback**: Modify `TomasuloEngine.issueStep()` and `writebackStep()` to handle multiple operations per cycle
2. **Branch prediction**: Add PC tracking and queue flushing in `TomasuloEngine`
3. **Real arithmetic**: Replace synthetic values with actual computation in `writebackStep()`
4. **Store buffer**: Add separate store buffer with address/value pairs
5. **ROB (Reorder Buffer)**: Add in-order commit stage for precise exceptions

## Troubleshooting

### Maven not found
Install Maven from https://maven.apache.org/ and add to PATH.

### JavaFX errors
Ensure Java 11+ is installed. The `pom.xml` includes JavaFX dependencies automatically.

### GUI doesn't launch
Try:
```powershell
mvn clean install
mvn javafx:run
```

## Authors & Submission

**Team**: [Your Team Number]  
**Members**: [Names and IDs]  
**Submission Date**: December 5, 2025  

## License

Educational project for KFUPM Microprocessors course.

# Microprocessors