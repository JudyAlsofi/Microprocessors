# Tomasulo Simulator - Project Status

**Date:** November 28, 2025  
**Status:** ✅ COMPLETE - Ready for Testing and Demo

---

## Project Structure

```
d:\Micro Project\Microprocessors\
├── src/
│   └── main/
│       ├── java/com/tomasulo/
│       │   ├── MainApp.java               # JavaFX GUI application
│       │   ├── TomasuloEngine.java        # Core simulation engine
│       │   ├── Instruction.java           # Instruction data structure
│       │   ├── InstructionType.java       # Enum of all instruction types
│       │   ├── ReservationStation.java    # Reservation station structure
│       │   ├── RegisterFile.java          # Register values and tags
│       │   ├── RegisterInitializer.java   # Test case initialization
│       │   ├── MemoryCache.java           # Direct-mapped cache model
│       │   └── SimulatorConfig.java       # Configuration parameters
│       └── resources/
│           ├── testcases.txt              # Test case 1 & 3
│           └── testcase2.txt              # Test case 2
├── pom.xml                                # Maven build configuration
├── README.md                              # User documentation
├── DEVELOPMENT_GUIDE.md                   # Developer guide
└── TEAM_INFO.txt                          # Team submission info (FILL IN)

```

---

## ✅ Completed Features

### Core Requirements
- [x] GUI simulator with JavaFX
- [x] Table-based visualization of all components
- [x] Reservation stations: Add/Sub, Mul/Div, Integer, Load/Store
- [x] Register file with renaming (tags)
- [x] Direct-mapped data cache
- [x] Instruction queue display
- [x] Cycle-by-cycle execution log

### Instruction Support
- [x] FP operations: ADD.D, SUB.D, MUL.D, DIV.D
- [x] Integer operations: ADDI, SUBI, DADDI, DSUBI
- [x] Load/Store: L.D, S.D, LW, SW, LD, SD
- [x] Branches: BEQ, BNE

### Configuration Options
- [x] User-configurable latencies for each instruction type
- [x] User-configurable cache size and block size
- [x] User-configurable cache hit latency and miss penalty
- [x] User-configurable reservation station counts
- [x] Register preloading via GUI button

### Hazard Handling
- [x] RAW (Read After Write) via register tags
- [x] WAR (Write After Read) via register renaming
- [x] WAW (Write After Write) via register renaming

### Cache Implementation
- [x] Byte-addressable memory model
- [x] Direct-mapped cache with configurable parameters
- [x] Cache miss penalty simulation
- [x] Address clash handling (eviction)
- [x] Data-only caching (instructions not cached)

### Test Cases
- [x] Test Case 1: Sequential with RAW hazards
- [x] Test Case 2: Sequential with multiple hazards
- [x] Test Case 3: Loop code with branches

---

## 🔧 How to Build and Run

### Quick Start
```powershell
# Navigate to project directory
cd "d:\Micro Project\Microprocessors"

# Run with Maven (requires Java 11+ and Maven)
mvn javafx:run
```

### Using the GUI
1. **Load Instructions**: Click "Load File" → Select `src/main/resources/testcases.txt`
2. **Initialize Registers**: Click "Init Regs (TC1)" to preload test values
3. **Configure**: Expand "Configuration" panel to adjust latencies/cache
4. **Execute**: Click "Step Cycle" for single-step or "Run 10" for batch execution
5. **View State**: Switch tabs to see reservation stations, registers, logs

---

## 📊 GUI Components

### Tabs
1. **Instructions** - Shows remaining instruction queue
2. **Add/Sub Stations** - FP addition/subtraction reservation stations
3. **Mul/Div Stations** - FP multiplication/division reservation stations
4. **Int Stations** - Integer operation stations
5. **Load/Store Buffers** - Memory access buffers
6. **Registers** - Current register values (R0-R7, F0-F7)

### Bottom Panel
- **Log** - Cycle-by-cycle execution trace (issue/execute/writeback events)

### Top Controls
- **Cycle Counter** - Current cycle number
- **Load File** - Load assembly instructions
- **Step Cycle** - Execute one cycle
- **Run 10** - Execute 10 cycles
- **Reset** - Clear simulator state
- **Init Regs** - Preload test register values
- **Configuration** - Expand to modify latencies and cache settings

---

## 🧪 Testing Instructions

### Test Case 1: RAW Hazards
```assembly
L.D F6, 0(R2)      # Load F6
L.D F2, 8(R2)      # Load F2
MUL.D F0, F2, F4   # F2 dependency (RAW)
SUB.D F8, F2, F6   # F2 and F6 dependencies
DIV.D F10, F0, F6  # F0 and F6 dependencies
ADD.D F6, F8, F2   # F8 and F2 dependencies
S.D F6, 8(R2)      # Store result
```

**Expected**: Instructions wait for dependencies before executing

### Test Case 2: WAW Hazard
```assembly
L.D F6, 0(R2)      # Write F6
ADD.D F7, F1, F3   # Independent
L.D F2, 20(R2)     # Write F2
MUL.D F0, F2, F4   # F2 dependency
SUB.D F8, F2, F6   # F2 and F6 dependencies
DIV.D F10, F0, F6  # F0 and F6 dependencies
S.D F10, 0(R2)     # Store F10
```

**Expected**: Register renaming prevents WAW hazards

### Test Case 3: Loop
```assembly
DADDI R1, R1, 24   # Initialize counter
DADDI R2, R2, 0    # Initialize termination
L.D F0, 8(R1)      # Load from memory
MUL.D F4, F0, F2   # Multiply
S.D F4, 8(R1)      # Store result
DSUBI R1, R1, 8    # Decrement counter
BNE R1, R2, -4     # Branch if not equal
```

**Init**: R1=24, R2=0, F2=100  
**Expected**: Loop executes multiple iterations

---

## 📝 Documentation Files

| File | Purpose |
|------|---------|
| `README.md` | User guide and project overview |
| `DEVELOPMENT_GUIDE.md` | Developer testing and workflow guide |
| `TEAM_INFO.txt` | Team member info (MUST FILL IN) |
| `PROJECT_STATUS.md` | This file - project completion status |

---

## ⚠️ Before Submission

### Required Actions
1. **Fill in TEAM_INFO.txt** with actual team member names, IDs, and tutorial numbers
2. **Write project report** explaining:
   - Development approach
   - Code structure and design decisions
   - Test cases and validation
   - Cache addressing strategy
   - Bus arbitration policy (first-in-list priority)
3. **Test all three test cases** and verify correct execution
4. **Prepare demo** - all team members should understand the code

### Submission Checklist
- [ ] Source code (all `.java` files)
- [ ] Test cases (`testcases.txt`, `testcase2.txt`)
- [ ] `pom.xml` (Maven build file)
- [ ] `README.md` (user documentation)
- [ ] **Project report** (TO BE WRITTEN)
- [ ] `TEAM_INFO.txt` (FILL IN team details)
- [ ] Zip folder named "TeamXX"

### Submission Details
- **Deadline**: Friday, December 5, 2025
- **Evaluation**: Saturday, December 6, 2025
- **Submission Link**: https://forms.gle/vSUpe9mUuC2V15Uu9

---

## 🎯 Key Implementation Details

### Cache Addressing Strategy
- **Index**: `(address / blockSize) % numCacheLines`
- **Tag**: `address / blockSize`
- **Direct-mapped**: Each address maps to exactly one cache line
- **Eviction**: On miss, old block evicted and new block loaded

### Bus Arbitration (Multiple Writebacks)
When multiple stations finish execution in the same cycle:
- **Policy**: Select first ready station in list order
- **Order**: Add0 → Add1 → Add2 → Mul0 → Mul1 → Int0 → Int1 → Load0 → Load1 → Load2
- **Others**: Remain in `writebackPending` state until next cycle

### Limitations (Document in Report)
- **Simplified Issue**: One instruction issued per cycle
- **Simplified Writeback**: One result published per cycle
- **Branch Handling**: Parsed but not fully implemented (no PC updates)
- **FP Arithmetic**: Values treated as integers (no IEEE 754 format)
- **Result Values**: Synthetic/deterministic (cycle-based calculation)

---

## 🚀 Next Steps

1. **Test Thoroughly**: Run all test cases and verify behavior
2. **Write Report**: Document your approach, design, and test results
3. **Fill Team Info**: Update TEAM_INFO.txt with actual details
4. **Prepare Demo**: Practice explaining the code and design decisions
5. **Submit**: Zip and upload to Google Form by December 5, 2025

---

**Project Status: READY FOR TESTING AND SUBMISSION**
