# Instruction File Format Guide

## File Format Rules

1. **One instruction per line**
2. **Comments**: Lines starting with `#` are ignored
3. **Empty lines**: Are ignored
4. **Case insensitive**: Instructions are converted to uppercase
5. **Whitespace**: Extra spaces are trimmed

## Supported Instruction Formats

### 1. Load Instructions
**Format**: `L.D dest, offset(base)` or `LW dest, offset(base)`

**Examples**:
```
L.D F6, 0(R2)
LW R1, 100(R2)
LD F0, 8(R1)
L.S F4, 16(R3)
```

**Components**:
- `dest`: Destination register (F0-F31 for FP, R0-R31 for integer)
- `offset`: Integer offset (can be negative)
- `base`: Base register (R0-R31)

---

### 2. Store Instructions
**Format**: `S.D src, offset(base)` or `SW src, offset(base)`

**Examples**:
```
S.D F6, 8(R2)
SW R1, 0(R2)
SD F4, 16(R1)
S.W F0, -4(R3)
```

**Components**:
- `src`: Source register to store
- `offset`: Integer offset (can be negative)
- `base`: Base register

---

### 3. Floating-Point Arithmetic
**Format**: `ADD.D dest, src1, src2` or `MUL.D dest, src1, src2`

**Examples**:
```
ADD.D F0, F1, F2
SUB.D F8, F2, F6
MUL.D F4, F2, F6
DIV.D F10, F0, F6
ADD F0, F1, F2
MUL F4, F2, F6
```

**Components**:
- `dest`: Destination register (F0-F31)
- `src1`: First source register
- `src2`: Second source register

---

### 4. Integer Arithmetic (with immediate)
**Format**: `ADDI dest, src, immediate` or `DADDI dest, src, immediate`

**Examples**:
```
ADDI R1, R2, 10
SUBI R3, R1, 5
DADDI R1, R1, 24
DSUBI R1, R1, 8
```

**Components**:
- `dest`: Destination register (R0-R31)
- `src`: Source register
- `immediate`: Integer value (can be negative)

---

### 5. Branch Instructions
**Format**: `BEQ src1, src2, offset` or `BNE src1, src2, offset`

**Examples**:
```
BEQ R1, R2, 0
BNE R1, R2, -4
BEQ R3, R4, 8
```

**Components**:
- `src1`: First source register to compare
- `src2`: Second source register to compare
- `offset`: Branch offset (can be negative for backward branches)

---

## Complete Example File

```assembly
# Test Case 1: Sequential code with RAW hazards
L.D F6, 0(R2)
L.D F2, 8(R2)
MUL.D F0, F2, F4
SUB.D F8, F2, F6
DIV.D F10, F0, F6
ADD.D F6, F8, F2
S.D F6, 8(R2)

# Test Case 2: WAW hazard
L.D F6, 0(R2)
ADD.D F7, F1, F3
L.D F2, 20(R2)
MUL.D F0, F2, F4
SUB.D F8, F2, F6
DIV.D F10, F0, F6
S.D F10, 0(R2)

# Test Case 3: Loop with branches
DADDI R1, R1, 24
DADDI R2, R2, 0
L.D F0, 8(R1)
MUL.D F4, F0, F2
S.D F4, 8(R1)
DSUBI R1, R1, 8
BNE R1, R2, -4
```

---

## Important Notes

1. **Register Names**:
   - Integer registers: `R0`, `R1`, `R2`, ..., `R31`
   - Floating-point registers: `F0`, `F1`, `F2`, ..., `F31`

2. **Instruction Names**:
   - Use dots for FP operations: `ADD.D`, `MUL.D`, `SUB.D`, `DIV.D`
   - Or without dots: `ADD`, `MUL`, `SUB`, `DIV`
   - Load/Store: `L.D`, `L.S`, `LW`, `LD`, `S.D`, `S.W`, `SW`, `SD`

3. **Address Format**:
   - Must be: `offset(base)` with parentheses
   - Examples: `0(R2)`, `100(R2)`, `-4(R1)`, `8(R1)`
   - No spaces inside parentheses

4. **Commas**:
   - Separate operands with commas
   - Example: `ADD.D F0, F1, F2` (not `ADD.D F0 F1 F2`)

5. **Labels**:
   - Labels like `LOOP:` are parsed but not used (branches don't update PC)

6. **Whitespace**:
   - Spaces around commas are optional: `F0, F1, F2` or `F0,F1,F2` both work
   - Extra spaces are trimmed automatically

---

## Common Errors to Avoid

❌ **Wrong**: `L.D F6,0(R2)` (missing space after comma - might work but not recommended)
✅ **Correct**: `L.D F6, 0(R2)`

❌ **Wrong**: `ADD.D F0 F1 F2` (missing commas)
✅ **Correct**: `ADD.D F0, F1, F2`

❌ **Wrong**: `LW R1, 100 R2` (missing parentheses)
✅ **Correct**: `LW R1, 100(R2)`

❌ **Wrong**: `BEQ R1, R2` (missing offset)
✅ **Correct**: `BEQ R1, R2, 0`

---

## Quick Reference

| Instruction Type | Format | Example |
|-----------------|--------|---------|
| Load | `L.D dest, offset(base)` | `L.D F6, 0(R2)` |
| Store | `S.D src, offset(base)` | `S.D F6, 8(R2)` |
| FP Add | `ADD.D dest, src1, src2` | `ADD.D F0, F1, F2` |
| FP Multiply | `MUL.D dest, src1, src2` | `MUL.D F4, F2, F6` |
| Integer Add | `ADDI dest, src, imm` | `ADDI R1, R2, 10` |
| Branch Equal | `BEQ src1, src2, offset` | `BEQ R1, R2, 0` |
| Branch Not Equal | `BNE src1, src2, offset` | `BNE R1, R2, -4` |

---

## Testing Your File

1. Save your instructions in a `.txt` file
2. Click "Load File" in the simulator
3. Select your file
4. Check the log for any parsing errors
5. If successful, instructions appear in the Instruction Queue

