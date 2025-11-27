# Development and Testing Guide

## Quick Start for Development

### 1. Initial Setup

```powershell
# Clone the repository (if applicable)
cd "d:\Micro Project\Microprocessors"

# Verify Maven installation
mvn --version

# Clean and compile
mvn clean compile
```

### 2. Running the Simulator

```powershell
# Run with Maven
mvn javafx:run

# Or compile and run manually
mvn clean package
java --module-path "C:\path\to\javafx-sdk\lib" --add-modules javafx.controls,javafx.fxml -jar target/tomasulo-simulator-1.0-SNAPSHOT.jar
```

## Testing Workflow

### Basic Testing Steps

1. **Launch the simulator**
   ```powershell
   mvn javafx:run
   ```

2. **Initialize registers** (optional but recommended for meaningful results)
   - Click "Init Regs (TC1)" button to preload R2, F4, F1, F3 with test values
   - Or manually edit `RegisterFile` initialization in code

3. **Load a test case**
   - Click "Load File"
   - Navigate to `src/main/resources/testcases.txt` (or `testcase2.txt`)
   - Observe instruction queue populated

4. **Configure parameters** (optional)
   - Expand "Configuration" panel
   - Modify latencies (e.g., Mul Latency = 10, Div Latency = 40)
   - Modify cache (e.g., Cache Size = 1024, Block Size = 16)
   - Click "Apply Config" (this resets the engine)

5. **Step through execution**
   - Click "Step Cycle" to advance one cycle at a time
   - Observe:
     - Instruction queue shrinks as instructions issue
     - Reservation stations fill with instructions
     - Vj/Vk populated when operands ready
     - Qj/Qk show dependencies
     - Log shows issue/execute/writeback events

6. **Verify results**
   - Check "Registers" tab for updated values
   - Check log for cache hits/misses
   - Verify stations clear after writeback

### Test Case Validation

#### Test Case 1: RAW Hazards
**Expected behavior:**
- `L.D F6, 0(R2)` issues first
- `L.D F2, 8(R2)` issues next (no dependency)
- `MUL.D F0, F2, F4` must wait for F2 (Qj = Load station)
- `SUB.D F8, F2, F6` waits for both F2 and F6
- `DIV.D F10, F0, F6` waits for F0 (Qj = Mul station) and F6
- `ADD.D F6, F8, F2` waits for F8
- `S.D F6, 8(R2)` waits for F6

**Validation checklist:**
- [ ] Instructions issue in order
- [ ] Dependencies correctly tracked via Qj/Qk
- [ ] Writeback clears dependencies
- [ ] No incorrect data forwarding

#### Test Case 2: WAW Hazard
**Expected behavior:**
- Two instructions write to F10: `MUL.D F0, F2, F4` produces F0, then `DIV.D F10, F0, F6`
- Register renaming handles WAW correctly

**Validation checklist:**
- [ ] F10 tag updated on each issue
- [ ] Final F10 value corresponds to DIV.D result
- [ ] No lost updates

#### Test Case 3: Loop
**Expected behavior:**
- `DADDI R1, R1, 24` sets R1 to 24
- Loop body executes with R1 decreasing by 8 each iteration
- `BNE R1, R2, -4` branches back (note: basic branch support only)

**Validation checklist:**
- [ ] R1 updates correctly
- [ ] Load/store addresses computed correctly
- [ ] Loop iterations logged

### Cache Testing

1. **Test cache hits**
   - Set Cache Size = 1024, Block Size = 16
   - Load test case with multiple loads to nearby addresses
   - Observe "Cache access" messages in log showing hit latency

2. **Test cache misses**
   - First access to any address should miss
   - Subsequent accesses to same block should hit
   - Verify miss penalty + hit latency reported

3. **Test cache conflicts**
   - Access addresses that map to same cache line (different tags)
   - Observe evictions and misses

### Performance Testing

1. **Large latency values**
   - Set Mul Latency = 50, Div Latency = 100
   - Observe instructions execute over many cycles
   - Verify remaining cycles decrement correctly

2. **Station saturation**
   - Set Add Stations = 1, Mul Stations = 1
   - Load test case with multiple operations
   - Observe structural hazards (instructions stall waiting for free station)

3. **High cache penalty**
   - Set Miss Penalty = 100
   - Observe loads take much longer on first access

## Code Structure Overview

### Main Components

1. **MainApp.java** (≈400 lines)
   - JavaFX GUI setup
   - Event handlers for buttons
   - Table views for stations, registers
   - Configuration panel

2. **TomasuloEngine.java** (≈200 lines)
   - Core simulation logic
   - `step()`: Executes one cycle (issue → execute → writeback)
   - `issueStep()`: Allocates instruction to station, performs register renaming
   - `executeStep()`: Checks operand readiness, decrements remaining cycles
   - `writebackStep()`: Broadcasts result, clears dependencies

3. **MemoryCache.java** (≈80 lines)
   - Direct-mapped cache model
   - `access()`: Returns latency (hit or miss)
   - `readWord()`, `writeWord()`: Byte-addressable memory

4. **Instruction.java** (≈30 lines)
   - Instruction model: type, dest, src1, src2, immediate

5. **ReservationStation.java** (≈30 lines)
   - Station state: busy, inst, Vj, Vk, Qj, Qk, remaining

6. **RegisterFile.java** (≈40 lines)
   - Register values + tags for renaming

### Key Algorithms

#### Issue Stage
```java
1. Check if instruction queue empty → return
2. Get next instruction
3. Select appropriate station pool (add/mul/load/int)
4. Find free station → if none, stall
5. Allocate station:
   - Mark busy
   - Check src1: if register has tag, set Qj; else set Vj
   - Check src2: if register has tag, set Qk; else set Vk
   - Set dest register tag to this station
6. Remove instruction from queue
```

#### Execute Stage
```java
For each busy station:
  If not executing:
    If Qj == null and Qk == null:
      Mark executing, start countdown
  If executing:
    Decrement remaining
    If remaining == 0:
      Mark writebackPending
```

#### Writeback Stage
```java
1. Find first station with writebackPending
2. Compute result value
3. For load/store: perform cache access, add latency to log
4. Update destination register value and clear tag
5. For all other stations:
   - If Qj matches this station: copy result to Vj, clear Qj
   - If Qk matches this station: copy result to Vk, clear Qk
6. Clear station (mark not busy)
```

## Extending the Simulator

### Adding New Instruction Types

1. Add to `InstructionType.java` enum
2. Update parser in `MainApp.parse()`
3. Add to `TomasuloEngine.selectPool()` to assign station type
4. Add to `TomasuloEngine.estimateLatency()` for execution time

### Adding Store Buffer

Currently stores use load buffers. To separate:

1. Create `StoreBuffer` class similar to `ReservationStation`
2. Add `List<StoreBuffer> storeBuffers` to `TomasuloEngine`
3. In issue stage, allocate stores to store buffer
4. In execute stage, wait for address and data operands
5. In writeback stage, write to memory/cache

### Adding Reorder Buffer (ROB)

For in-order commit:

1. Create `ROBEntry` class: {inst, dest, value, ready}
2. Add circular buffer in `TomasuloEngine`
3. Issue stage: allocate ROB entry, tag dest with ROB ID
4. Writeback stage: mark ROB entry ready
5. Commit stage: retire head of ROB in order

### Implementing Branch Prediction

1. Add PC tracking to `TomasuloEngine`
2. On branch instruction:
   - Predict taken/not-taken
   - Speculatively fetch from target or PC+1
3. On branch resolution:
   - If misprediction: flush instruction queue and ROB
   - Update PC to correct target

## Common Issues

### Issue: GUI doesn't show updates after step
**Solution:** Ensure `refreshUI()` is called after `engine.step()`

### Issue: Instructions never execute
**Solution:** Check that source operands are initialized (use "Init Regs" button or preload in `RegisterFile` constructor)

### Issue: Cache always misses
**Solution:** Verify block size and cache size are power of 2 and reasonable (e.g., 16B block, 1KB cache)

### Issue: Stations never clear
**Solution:** Check `writebackStep()` logic; ensure `station.clear()` is called after broadcast

### Issue: Maven build fails
**Solution:** Verify Java 11+ and Maven 3.6+:
```powershell
java -version
mvn -version
```

## Performance Profiling

To measure simulator performance:

1. Load large test case (e.g., 100 instructions)
2. Use "Run 10" repeatedly and measure time
3. Profile with VisualVM or JProfiler if needed

Typical performance: ~1000 cycles/second on modern hardware.

## Submission Checklist

- [ ] All test cases run successfully
- [ ] README.md documents approach and usage
- [ ] Code is well-commented
- [ ] Configuration works correctly
- [ ] Cache hits/misses logged properly
- [ ] Hazards handled correctly (RAW, WAR, WAW)
- [ ] GUI displays all required tables
- [ ] Team info file created (names, IDs, tutorial)
- [ ] Project zipped as "TeamXX.zip"

## Contact & Support

For questions during development:
1. Review this guide and README.md
2. Check code comments in `TomasuloEngine.java`
3. Test with provided test cases first
4. Ask TA during office hours

Good luck with your project! 🚀
