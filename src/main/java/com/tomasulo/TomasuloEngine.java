package com.tomasulo;

import java.util.*;

// Enhanced Tomasulo engine with full load/store/branch support
public class TomasuloEngine {
    public final SimulatorConfig cfg;
    public final List<Instruction> instrQueue = new ArrayList<>();
    public final List<ReservationStation> addStations = new ArrayList<>();
    public final List<ReservationStation> mulStations = new ArrayList<>();
    public final List<ReservationStation> intStations = new ArrayList<>();
    public final List<ReservationStation> loadBuffers = new ArrayList<>();
    public final RegisterFile registers = new RegisterFile();
    public final MemoryCache cache;

    public int cycle = 0;
    public final List<String> history = new ArrayList<>();
    public int pc = 0; // program counter for branch handling
    private int issuedCount = 0; // track how many instructions have been issued
    private final List<Instruction> originalProgram = new ArrayList<>();

    public TomasuloEngine(SimulatorConfig cfg) {
        this.cfg = cfg;
        for (int i = 0; i < cfg.numAddStations; i++) addStations.add(new ReservationStation("Add" + i));
        for (int i = 0; i < cfg.numMulStations; i++) mulStations.add(new ReservationStation("Mul" + i));
        for (int i = 0; i < cfg.numIntStations; i++) intStations.add(new ReservationStation("Int" + i));
        for (int i = 0; i < cfg.numLoadBuffers; i++) loadBuffers.add(new ReservationStation("Load" + i));
        this.cache = new MemoryCache(cfg.cacheSizeBytes, cfg.blockSizeBytes, cfg.cacheHitLatency, cfg.cacheMissPenalty);
    }

    public void loadInstructions(List<Instruction> ins) {
        instrQueue.clear();
        instrQueue.addAll(ins);
        originalProgram.clear();
        originalProgram.addAll(ins);
        pc = 0;
        issuedCount = 0;
    }

    // Very simplified: each cycle we try to issue 1 instruction, then update executing stations, then writeback at most 1 result.
    public void step() {
        cycle++;
        history.add("Cycle " + cycle + ": start");
        
        // Clear justIssued flags from previous cycle
        List<ReservationStation> all = new ArrayList<>();
        all.addAll(addStations); all.addAll(mulStations); all.addAll(intStations); all.addAll(loadBuffers);
        for (ReservationStation rs : all) {
            if (rs.busy) rs.justIssued = false;
        }
        
        // Phase 1: Writeback (broadcast results from previous cycle)
        writebackStep();
        
        // Phase 2: Issue new instruction
        issueStep();
        
        // Phase 3: Execute (start execution for ready instructions, decrement counters)
        executeStep();
        
        history.add("Cycle " + cycle + ": end");
    }

    private void issueStep() {
        if (instrQueue.isEmpty()) return;
        Instruction ins = instrQueue.get(0);
        // decide station
        List<ReservationStation> pool = selectPool(ins);
        if (pool == null) return; // unsupported
        ReservationStation free = null;
        for (ReservationStation rs : pool) if (!rs.busy) { free = rs; break; }
        if (free == null) return; // stall

        // perform register renaming
        free.busy = true;
        free.inst = ins;
        free.executing = false;
        free.writebackPending = false;
        free.addressReady = false;
        free.justIssued = true; // Mark as just issued to prevent execution this cycle
        
        // For loads/stores and branches, handle base register
        if (ins.type == InstructionType.LD || ins.type == InstructionType.LW || 
            ins.type == InstructionType.L_D || ins.type == InstructionType.L_S ||
            ins.type == InstructionType.SD || ins.type == InstructionType.SW ||
            ins.type == InstructionType.S_S || ins.type == InstructionType.S_D || ins.type == InstructionType.S_W) {
            // src1 is base register for address calculation
            if (ins.src1 != null) {
                String t = registers.getTag(ins.src1);
                if (t != null) free.qj = t; else free.vj = registers.get(ins.src1);
            }
            // For stores, src2 is the value to store
            if (ins.type == InstructionType.SD || ins.type == InstructionType.SW ||
                ins.type == InstructionType.S_S || ins.type == InstructionType.S_D || ins.type == InstructionType.S_W) {
                if (ins.src2 != null) {
                    String t = registers.getTag(ins.src2);
                    if (t != null) free.qk = t; else free.vk = registers.get(ins.src2);
                }
            }
        } else if (ins.type == InstructionType.BEQ || ins.type == InstructionType.BNE) {
            // Branches need both source registers
            if (ins.src1 != null) {
                String t = registers.getTag(ins.src1);
                if (t != null) free.qj = t; else free.vj = registers.get(ins.src1);
            }
            if (ins.src2 != null) {
                String t = registers.getTag(ins.src2);
                if (t != null) free.qk = t; else free.vk = registers.get(ins.src2);
            }
        } else if (ins.type == InstructionType.ADDI || ins.type == InstructionType.SUBI ||
                   ins.type == InstructionType.DADDI || ins.type == InstructionType.DSUBI) {
            // Integer immediate instructions: src1 is register, immediate goes to Vk
            if (ins.src1 != null) {
                String t = registers.getTag(ins.src1);
                if (t != null) free.qj = t; else free.vj = registers.get(ins.src1);
            }
            // Put immediate value directly in Vk (no dependency)
            if (ins.immediate != null) {
                free.vk = ins.immediate;
            }
        } else {
            // Regular ALU ops: sources
            if (ins.src1 != null) {
                String t = registers.getTag(ins.src1);
                if (t != null) free.qj = t; else free.vj = registers.get(ins.src1);
            }
            if (ins.src2 != null) {
                String t = registers.getTag(ins.src2);
                if (t != null) free.qk = t; else free.vk = registers.get(ins.src2);
            }
        }
        
        // destination tagging (not for stores or branches)
        if (ins.dest != null && 
            ins.type != InstructionType.SD && ins.type != InstructionType.SW &&
            ins.type != InstructionType.S_S && ins.type != InstructionType.S_D && ins.type != InstructionType.S_W &&
            ins.type != InstructionType.BEQ && ins.type != InstructionType.BNE) {
            registers.setTag(ins.dest, free.name);
        }

        // set basic remaining cycles
        free.remaining = estimateLatency(ins);

        history.add("Issued " + ins + " to " + free.name);
        instrQueue.remove(0);
        issuedCount++; // Track that we issued an instruction
    }

    private List<ReservationStation> selectPool(Instruction ins) {
        switch (ins.type) {
            case ADD: case SUB: case ADD_D: case SUB_D: case ADD_S: case SUB_S: return addStations;
            case MUL: case DIV: case MUL_D: case DIV_D: case MUL_S: case DIV_S: return mulStations;
            case LD: case LW: case L_D: case L_S: case SD: case SW: case S_S: case S_D: case S_W: return loadBuffers;
            case ADDI: case SUBI: case DADDI: case DSUBI: return intStations;
            case BEQ: case BNE: return intStations;
            default: return addStations;
        }
    }

    private int estimateLatency(Instruction ins) {
        switch (ins.type) {
            case MUL: case MUL_D: case MUL_S: return cfg.mulLatency;
            case DIV: case DIV_D: case DIV_S: return cfg.divLatency;
            case ADD: case SUB: case ADD_D: case SUB_D: case ADD_S: case SUB_S: return cfg.addLatency;
            case LD: case LW: case L_D: case L_S: return cfg.loadLatency;
            case SD: case SW: case S_S: case S_D: case S_W: return cfg.storeLatency;
            case ADDI: case SUBI: case DADDI: case DSUBI: return cfg.intLatency;
            default: return cfg.intLatency;
        }
    }

    private void executeStep() {
        // For each station: compute address if needed, start execution when ready, decrement cycles
        List<ReservationStation> all = new ArrayList<>();
        all.addAll(addStations); all.addAll(mulStations); all.addAll(intStations); all.addAll(loadBuffers);
        
        for (ReservationStation rs : all) {
            if (!rs.busy) continue;
            
            // Skip if just issued this cycle - cannot start execution until next cycle
            if (rs.justIssued) continue;
            
            // For load/store: check if another load/store is already executing
            // Only ONE load/store can execute at a time (single load/store unit)
            if (isLoadOrStore(rs.inst) && !rs.executing) {
                boolean anotherLoadStoreExecuting = false;
                for (ReservationStation other : loadBuffers) {
                    if (other != rs && other.busy && other.executing) {
                        anotherLoadStoreExecuting = true;
                        break;
                    }
                }
                if (anotherLoadStoreExecuting) continue; // Wait for other load/store to finish
            }
            
            // For load/store: compute effective address when base register ready
            if (!rs.addressReady && isLoadOrStore(rs.inst)) {
                if (rs.qj == null) { // base register ready
                    int base = (rs.vj == null) ? 0 : rs.vj;
                    int offset = (rs.inst.immediate == null) ? 0 : rs.inst.immediate;
                    rs.address = base + offset;
                    rs.addressReady = true;
                    history.add(rs.name + " computed address: " + rs.address);
                    
                    // For loads, immediately check cache to detect miss/hit and start cache access countdown
                    if (isLoad(rs.inst)) {
                        int cacheAccessLatency = cache.access(rs.address, 4);
                        // cacheAccessLatency = hitLatency (2) on hit, or (hitLatency + missPenalty) (12) on miss
                        
                        if (cacheAccessLatency > cfg.cacheHitLatency) {
                            // Cache miss - need to count down cache access latency (includes hit + miss penalty)
                            rs.cacheMissPenalty = cacheAccessLatency;
                            rs.remaining = cfg.loadLatency; // Load execution comes after cache access
                            rs.cacheBlockLoaded = false;
                            rs.executing = true; // Mark as executing so cache access countdown starts
                            history.add(rs.name + " cache MISS at addr " + rs.address + 
                                      " (cache access latency=" + cacheAccessLatency + ", load latency=" + cfg.loadLatency + ")");
                        } else {
                            // Cache hit - need to count down hit latency before load execution
                            rs.cacheMissPenalty = cacheAccessLatency; // Hit latency countdown
                            rs.remaining = cfg.loadLatency; // Load execution comes after hit latency
                            rs.cacheBlockLoaded = true; // Already in cache, just need hit latency
                            rs.executing = true; // Mark as executing so hit latency countdown starts
                            history.add(rs.name + " cache HIT at addr " + rs.address + 
                                      " (hit latency=" + cacheAccessLatency + ", load latency=" + cfg.loadLatency + ")");
                        }
                    }
                }
            }
            
            // For stores: check cache when both address AND store value are ready
            if (isStore(rs.inst) && rs.addressReady && !rs.executing && rs.qk == null) {
                int cacheAccessLatency = cache.access(rs.address, 4);
                // cacheAccessLatency = hitLatency (2) on hit, or (hitLatency + missPenalty) (12) on miss
                
                if (cacheAccessLatency > cfg.cacheHitLatency) {
                    // Cache miss - need to count down cache access latency (includes hit + miss penalty)
                    rs.cacheMissPenalty = cacheAccessLatency;
                    rs.remaining = cfg.storeLatency; // Store execution comes after cache access
                    rs.cacheBlockLoaded = false;
                    rs.executing = true; // Mark as executing so cache access countdown starts
                    history.add(rs.name + " cache MISS at addr " + rs.address + 
                              " (cache access latency=" + cacheAccessLatency + ", store latency=" + cfg.storeLatency + ")");
                } else {
                    // Cache hit - need to count down hit latency before store execution
                    rs.cacheMissPenalty = cacheAccessLatency; // Hit latency countdown
                    rs.remaining = cfg.storeLatency; // Store execution comes after hit latency
                    rs.cacheBlockLoaded = true; // Already in cache, just need hit latency
                    rs.executing = true; // Mark as executing so hit latency countdown starts
                    history.add(rs.name + " cache HIT at addr " + rs.address + 
                              " (hit latency=" + cacheAccessLatency + ", store latency=" + cfg.storeLatency + ")");
                }
            }
            
            // For load/store: check if another load/store is already executing (in actual execution phase)
            // Only ONE load/store can be in actual execution at a time (single load/store unit)
            boolean canExecuteLoadStore = true;
            if (isLoadOrStore(rs.inst) && rs.cacheBlockLoaded) {
                for (ReservationStation other : loadBuffers) {
                    if (other != rs && other.busy && other.executing && other.cacheBlockLoaded && other.remaining > 0) {
                        canExecuteLoadStore = false;
                        break;
                    }
                }
            }
            
            // Start execution when operands ready (for non-loads or stores)
            if (!rs.executing) {
                boolean canStart = false;
                
                if (isLoadOrStore(rs.inst)) {
                    // Loads/stores already handled above during address computation
                    canStart = false;
                } else if (rs.inst.type == InstructionType.BEQ || rs.inst.type == InstructionType.BNE) {
                    canStart = rs.qj == null && rs.qk == null; // both operands ready
                } else {
                    canStart = rs.qj == null && rs.qk == null; // regular ALU
                }
                
                if (canStart) {
                    rs.executing = true;
                    history.add(rs.name + " starts executing " + rs.inst);
                }
            }
            
            // Handle cache access latency countdown (hit latency or miss penalty+hit latency)
            // This happens BEFORE load execution phase, and doesn't require load/store unit
            if (rs.executing && rs.cacheMissPenalty > 0) {
                rs.cacheMissPenalty--;
                if (rs.cacheMissPenalty == 0) {
                    // Cache access complete
                    if (isLoad(rs.inst)) {
                        if (!rs.cacheBlockLoaded) {
                            // Was a miss - now bring block into cache
                            cache.loadBlockIntoCache(rs.address);
                            rs.cacheBlockLoaded = true;
                            history.add(rs.name + " cache block loaded for addr " + rs.address);
                        } else {
                            // Was a hit - cache access latency complete
                            history.add(rs.name + " cache hit latency complete for addr " + rs.address);
                        }
                    }
                }
            }
            
            // Decrement execution cycles (only after cache access is complete for loads/stores, and load/store unit is available)
            if (rs.executing && rs.remaining > 0) {
                // For loads, only decrement if cache access is done (cacheMissPenalty == 0) AND no other load/store is executing
                if (isLoad(rs.inst)) {
                    if (rs.cacheMissPenalty == 0 && canExecuteLoadStore) {
                        rs.remaining--;
                    }
                } else if (isStore(rs.inst)) {
                    // Stores: only decrement if cache access is done (cacheMissPenalty == 0) AND no other load/store is executing
                    if (rs.cacheMissPenalty == 0 && canExecuteLoadStore) {
                        rs.remaining--;
                    }
                } else {
                    // Non-load/store instructions execute normally
                    rs.remaining--;
                }
                if (rs.remaining == 0) {
                    rs.writebackPending = true;
                    history.add(rs.name + " finished execution of " + rs.inst);
                }
            }
        }
    }
    
    private boolean isLoadOrStore(Instruction ins) {
        return ins.type == InstructionType.LD || ins.type == InstructionType.LW ||
               ins.type == InstructionType.L_D || ins.type == InstructionType.L_S ||
               ins.type == InstructionType.SD || ins.type == InstructionType.SW ||
               ins.type == InstructionType.S_S || ins.type == InstructionType.S_D || ins.type == InstructionType.S_W;
    }
    
    private boolean isLoad(Instruction ins) {
        return ins.type == InstructionType.LD || ins.type == InstructionType.LW ||
               ins.type == InstructionType.L_D || ins.type == InstructionType.L_S;
    }
    
    private boolean isStore(Instruction ins) {
        return ins.type == InstructionType.SD || ins.type == InstructionType.SW ||
               ins.type == InstructionType.S_S || ins.type == InstructionType.S_D || ins.type == InstructionType.S_W;
    }

    private void writebackStep() {
        // Publish at most one result per cycle. 
        // ARBITRATION POLICY: First-come-first-served based on station list order
        // (Add0, Add1, Add2, Mul0, Mul1, Int0, Int1, Load0, Load1, Load2)
        List<ReservationStation> all = new ArrayList<>();
        all.addAll(addStations); all.addAll(mulStations); all.addAll(intStations); all.addAll(loadBuffers);
        
        ReservationStation ready = null;
        for (ReservationStation rs : all) {
            if (rs.busy && rs.writebackPending) {
                ready = rs;
                break; // First one wins the bus
            }
        }
        if (ready == null) return;

        // Handle different instruction types
        if (isStore(ready.inst)) {
            // Store: write value to memory/cache
            int storeValue = (ready.vk == null) ? 0 : ready.vk;
            cache.writeWord(ready.address, storeValue);
            history.add(ready.name + " writeback: Store value=" + storeValue + 
                       " to addr=" + ready.address);
        } else if (ready.inst.type == InstructionType.BEQ || ready.inst.type == InstructionType.BNE) {
            // Branch: compare operands and update PC if needed
            int val1 = (ready.vj == null) ? 0 : ready.vj;
            int val2 = (ready.vk == null) ? 0 : ready.vk;
            boolean condition = (ready.inst.type == InstructionType.BEQ) ? (val1 == val2) : (val1 != val2);
            
            if (condition) {
                // Branch taken - jump to target address
                int offset = (ready.inst.immediate == null) ? 0 : ready.inst.immediate;
                int targetPC = issuedCount + offset; // Calculate absolute target PC
                
                history.add(ready.name + " writeback: Branch TAKEN (offset=" + offset + 
                           "), val1=" + val1 + " val2=" + val2 + ", jumping to instruction " + targetPC);
                
                // Clear instruction queue and reload from target PC
                if (targetPC >= 0 && targetPC < originalProgram.size()) {
                    instrQueue.clear();
                    // Add instructions from target PC onwards
                    for (int i = targetPC; i < originalProgram.size(); i++) {
                        instrQueue.add(originalProgram.get(i));
                    }
                    issuedCount = targetPC; // Update issued count to target PC
                    history.add("Reloaded instruction queue from PC=" + targetPC);
                } else {
                    history.add("Branch target out of bounds: " + targetPC);
                }
            } else {
                history.add(ready.name + " writeback: Branch NOT TAKEN, val1=" + val1 + " val2=" + val2);
            }
        } else {
            // Regular ALU or Load: compute result and writeback
            int value;
            if (isLoad(ready.inst)) {
                // Load: read from memory (cache block already loaded during execution)
                value = cache.readWord(ready.address);
                history.add(ready.name + " writeback: Load value=" + value + " from addr=" + ready.address);
            } else {
                // ALU operation: compute result
                value = computeResult(ready);
                history.add(ready.name + " writeback: ALU result=" + value);
            }
            
            // Write to destination register
            if (ready.inst.dest != null) {
                registers.set(ready.inst.dest, value);
                registers.clearTag(ready.inst.dest, ready.name);
                history.add(ready.name + " wrote " + ready.inst.dest + "=" + value);
            }
            
            // Broadcast value to waiting stations
            for (ReservationStation rs : all) {
                if (!rs.busy) continue;
                if (ready.name.equals(rs.qj)) { 
                    rs.vj = value; 
                    rs.qj = null;
                    // Mark that this station just received a value - can't start execution this cycle
                    rs.justIssued = true;
                }
                if (ready.name.equals(rs.qk)) { 
                    rs.vk = value; 
                    rs.qk = null;
                    // Mark that this station just received a value - can't start execution this cycle
                    rs.justIssued = true;
                }
            }
        }

        // Clear station
        ready.clear();
    }
    
    private int computeResult(ReservationStation rs) {
        // Simplified result computation
        int v1 = (rs.vj == null) ? 0 : rs.vj;
        int v2 = (rs.vk == null) ? 0 : rs.vk;
        int imm = (rs.inst.immediate == null) ? 0 : rs.inst.immediate;
        
        switch (rs.inst.type) {
            case ADD: case ADD_D: case ADD_S: return v1 + v2;
            case SUB: case SUB_D: case SUB_S: return v1 - v2;
            case MUL: case MUL_D: case MUL_S: return v1 * v2;
            case DIV: case DIV_D: case DIV_S: return (v2 != 0) ? v1 / v2 : 0;
            case ADDI: case DADDI: return v1 + imm;
            case SUBI: case DSUBI: return v1 - imm;
            default: return cycle * 10 + Math.abs(Objects.hashCode(rs.inst.raw)) % 100;
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("cycle", cycle);
        m.put("instrQueue", new ArrayList<>(instrQueue));
        m.put("addStations", snapshotStations(addStations));
        m.put("mulStations", snapshotStations(mulStations));
        m.put("intStations", snapshotStations(intStations));
        m.put("loadBuffers", snapshotStations(loadBuffers));
        m.put("registers", new HashMap<>(registers.regs));
        m.put("registerTags", new HashMap<>(registers.tag));
        return m;
    }

    private List<Map<String, Object>> snapshotStations(List<ReservationStation> lst) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ReservationStation rs : lst) {
            Map<String, Object> s = new HashMap<>();
            s.put("name", rs.name);
            s.put("busy", rs.busy);
            s.put("inst", rs.inst == null ? null : rs.inst.toString());
            s.put("vj", rs.vj);
            s.put("vk", rs.vk);
            s.put("qj", rs.qj);
            s.put("qk", rs.qk);
            s.put("remaining", rs.remaining);
            out.add(s);
        }
        return out;
    }
}