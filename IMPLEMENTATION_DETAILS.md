# Implementation Details and Design Decisions

## Bus Arbitration Policy

### Problem: Multiple Instructions Finishing in Same Cycle
When two or more reservation stations finish execution in the same cycle and both want to write their results to the Common Data Bus (CDB), only ONE can be granted access per cycle.

### Our Solution: Static Priority (First-Come-First-Served by Station Order)
**Arbitration Order:**
1. Add/Sub Stations: Add0 → Add1 → Add2
2. Mul/Div Stations: Mul0 → Mul1
3. Integer Stations: Int0 → Int1
4. Load/Store Buffers: Load0 → Load1 → Load2

**How It Works:**
- In `writebackStep()`, we iterate through all reservation stations in the order listed above
- The **first** station found with `writebackPending = true` wins the bus
- All other stations with pending writebacks must wait until the next cycle
- This ensures deterministic, conflict-free bus access

**Code Implementation** (TomasuloEngine.java, line ~225):
```java
List<ReservationStation> all = new ArrayList<>();
all.addAll(addStations);  // Add0, Add1, Add2
all.addAll(mulStations);  // Mul0, Mul1
all.addAll(intStations);  // Int0, Int1
all.addAll(loadBuffers);  // Load0, Load1, Load2

for (ReservationStation rs : all) {
    if (rs.busy && rs.writebackPending) {
        ready = rs;
        break; // First one wins!
    }
}
```

**Alternative Policies (Not Implemented):**
- Age-based: Oldest issued instruction gets priority
- Round-robin: Fair rotation among stations
- Dynamic priority: Based on instruction type or criticality

---

## Cache Implementation

### Memory Model
- **Byte-addressable memory**: Each address stores 8 bits (1 byte)
- Modeled as `HashMap<Integer, Integer>` where key=address, value=byte
- Word operations (LW, L.D) read/write 4 consecutive bytes
- Double-word operations (LD) read/write 8 bytes

### Cache Architecture
- **Type**: Direct-mapped cache
- **Indexing**: `index = (address / blockSize) % numLines`
- **Tag**: `tag = address / blockSize`
- **Block**: Contiguous chunk of `blockSize` bytes

### Cache Operations

#### Read (Load Instructions)
1. Compute effective address: `EA = base_register + offset`
2. Compute cache index and tag from EA
3. **Cache Hit**: Block is present → return data in `hitLatency` cycles
4. **Cache Miss**: 
   - Fetch block from memory (takes `missPenalty` cycles)
   - Store block in cache line
   - Return data
   - Total latency: `missPenalty + hitLatency`

**Example**: `L.D F0, 8(R1)` with R1=1000
```
EA = 1000 + 8 = 1008
Block = 1008 / 16 = 63
Index = 63 % 64 = 63
Tag = 63

If cache[63].tag == 63 && cache[63].valid:
    HIT → read bytes 1008,1009,1010,1011 in 2 cycles
Else:
    MISS → fetch block [1008-1023] to cache[63]
         → read bytes 1008,1009,1010,1011
         → total 52 cycles (50 penalty + 2 hit)
```

#### Write (Store Instructions)
1. Compute effective address
2. Write data to memory immediately
3. **Write-allocate policy**: If block not in cache, bring it in
4. If block already in cache, mark as valid

**Code** (MemoryCache.java):
```java
public void writeWord(int address, int value) {
    // Write to memory
    for (int i = 0; i < 4; i++) {
        memory.put(address + i, (value >> (8*i)) & 0xFF);
    }
    // Update cache (write-allocate)
    int idx = indexOf(address);
    int tag = tagOf(address);
    if (!linesArr[idx].valid || linesArr[idx].tag != tag) {
        linesArr[idx].valid = true;
        linesArr[idx].tag = tag;
    }
}
```

### Address Clash Handling
**Problem**: Two addresses map to same cache line (e.g., addresses 0 and 1024 with 64 cache lines)

**Solution**: Direct-mapped eviction
- When new block needs same cache line, old block is **evicted**
- No warning or special handling
- Next access to old address will be a **compulsory miss**

**Example**:
```
Cache size = 1024 bytes
Block size = 16 bytes
Num lines = 64

Access addr 0:    block=0, index=0  → MISS, load to cache[0]
Access addr 16:   block=1, index=1  → MISS, load to cache[1]
Access addr 1024: block=64, index=0 → MISS, evicts block 0 from cache[0]
Access addr 0:    block=0, index=0  → MISS again! (was evicted)
```

---

## Instruction Handling

### Load Instructions (LD, LW, L.D, L.S)
**Issue Stage:**
- Reserve load buffer
- Check if base register ready, else tag dependency (Qj)
- Destination register tagged with buffer name

**Execute Stage:**
1. Wait for base register (Qj = null)
2. Compute effective address: `EA = base + offset`
3. Access cache with EA
4. If miss, execution time increased by miss penalty
5. Wait for cache latency cycles

**Writeback Stage:**
- Read word from memory/cache
- Write value to destination register
- Clear register tag
- Broadcast value to waiting stations

### Store Instructions (SD, SW, S.D, S.W)
**Issue Stage:**
- Reserve load buffer
- Tag dependencies: Qj (base), Qk (value to store)
- NO destination register tagging

**Execute Stage:**
1. Wait for base register ready
2. Compute effective address
3. Wait for value register ready
4. Perform store operation

**Writeback Stage:**
- Write value to memory at EA
- Update cache if applicable
- Release buffer (no register update)

### Branch Instructions (BEQ, BNE)
**Issue Stage:**
- Reserve integer station
- Tag both source operands (Qj, Qk)
- NO destination register

**Execute Stage:**
- Wait for both operands ready
- Compare values

**Writeback Stage:**
- Evaluate condition
- Log whether branch taken or not
- **Note**: PC update and pipeline flush NOT fully implemented (simplified model)

### ALU Instructions (ADD.D, SUB.D, MUL.D, DIV.D, etc.)
**Issue Stage:**
- Reserve appropriate station (Add, Mul, or Int)
- Tag operand dependencies
- Tag destination register

**Execute Stage:**
- Wait for operands ready
- Compute result based on operation

**Writeback Stage:**
- Write result to destination
- Broadcast to waiting stations
- Clear register tag

---

## Register Renaming (RAW/WAR/WAW Hazards)

### Register Status Table
Each register has:
- **Value**: Current integer value
- **Tag**: Name of reservation station that will produce next value (null if ready)

### Hazard Prevention

**RAW (Read After Write):**
```assembly
ADD.D F0, F2, F4    # F0 produced by Add0
MUL.D F6, F0, F8    # Needs F0 → Qj = Add0
```
- MUL issued with Qj="Add0" (not value yet)
- MUL waits until Add0 broadcasts result
- Add0 writeback clears Qj, sets Vj

**WAR (Write After Read):** - **Prevented by register renaming**
```assembly
SUB.D F8, F2, F6    # Reads F6 → Vk=<value>
ADD.D F6, F0, F2    # Writes F6 → tags F6 with Add0
```
- SUB captures F6's value immediately at issue
- ADD tags F6 but doesn't affect SUB (already has value)

**WAW (Write After Write):**
```assembly
L.D F6, 0(R2)       # F6 tagged with Load0
ADD.D F6, F8, F2    # F6 re-tagged with Add1
```
- Second writer overwrites register tag
- Only Add1's result reaches F6
- Load0's result is discarded (correct semantics!)

---

## Configuration Options

All parameters configurable via GUI:

### Latencies (cycles)
- Add/Sub operations: default 2
- Multiply: default 10
- Divide: default 40
- Integer ops: default 1
- Load/Store: default 2 (base, before cache latency)

### Cache Parameters
- Cache size: default 1024 bytes
- Block size: default 16 bytes
- Hit latency: default 2 cycles
- Miss penalty: default 50 cycles

### Station Counts
- Add/Sub stations: default 3
- Mul/Div stations: default 2
- Integer stations: default 2
- Load/Store buffers: default 3

---

## Limitations and Future Work

### Current Limitations
1. **Single issue per cycle**: Only one instruction dispatched per cycle
2. **Single writeback per cycle**: Bus shared by one station at a time
3. **No speculative execution**: Branches not predicted
4. **No pipeline flush**: Branch misprediction doesn't clear issued instructions
5. **Simplified result computation**: Real FP arithmetic not implemented
6. **No store buffer**: Stores treated same as loads

### Suggested Enhancements
1. **Multiple issue/writeback**: Model superscalar execution
2. **Branch prediction**: 2-bit saturating counter, branch target buffer
3. **Speculative execution**: Issue beyond branches, squash on misprediction
4. **Memory disambiguation**: Handle load/store ordering with addresses
5. **Realistic FP units**: IEEE 754 arithmetic
6. **Store buffer**: Separate handling of stores with forwarding
7. **Out-of-order commit**: Reorder buffer for precise exceptions

---

## Testing and Validation

### Test Case 1: RAW Hazards
All dependencies correctly identified and resolved via register renaming.

### Test Case 2: WAW Hazard
Second write correctly overwrites register tag.

### Test Case 3: Loop with Branches
Loop iterations execute correctly; branch comparison works.

### Cache Behavior
- First access to address → miss (logged with penalty)
- Repeated access to same block → hit
- Eviction on address clash observed

---

**Implementation Date**: December 2025  
**Language**: Java 11 with JavaFX  
**Total Lines of Code**: ~900 lines
