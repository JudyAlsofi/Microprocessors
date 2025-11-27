package com.tomasulo;

import java.util.*;

// Simplified Tomasulo engine sufficient for cycle-by-cycle demonstration.
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
    }

    // Very simplified: each cycle we try to issue 1 instruction, then update executing stations, then writeback at most 1 result.
    public void step() {
        cycle++;
        history.add("Cycle " + cycle + ": start");
        issueStep();
        executeStep();
        writebackStep();
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
        // sources
        if (ins.src1 != null) {
            String t = registers.getTag(ins.src1);
            if (t != null) free.qj = t; else free.vj = registers.get(ins.src1);
        }
        if (ins.src2 != null) {
            String t = registers.getTag(ins.src2);
            if (t != null) free.qk = t; else free.vk = registers.get(ins.src2);
        }
        // destination tagging
        if (ins.dest != null) registers.setTag(ins.dest, free.name);

        // set basic remaining cycles
        free.remaining = estimateLatency(ins);

        history.add("Issued " + ins + " to " + free.name);
        instrQueue.remove(0);
    }

    private List<ReservationStation> selectPool(Instruction ins) {
        switch (ins.type) {
            case ADD: case SUB: case ADD_D: case SUB_D: return addStations;
            case MUL: case DIV: case MUL_D: case DIV_D: return mulStations;
            case LD: case LW: case L_D: case L_S: case SD: case SW: case S_D: case S_W: return loadBuffers;
            case ADDI: case SUBI: case DADDI: case DSUBI: return intStations;
            default: return addStations;
        }
    }

    private int estimateLatency(Instruction ins) {
        switch (ins.type) {
            case MUL: case MUL_D: return cfg.mulLatency;
            case DIV: case DIV_D: return cfg.divLatency;
            case ADD: case SUB: case ADD_D: case SUB_D: return cfg.addLatency;
            case LD: case LW: case L_D: case L_S: return cfg.loadLatency;
            case SD: case SW: case S_D: case S_W: return cfg.storeLatency;
            case ADDI: case SUBI: case DADDI: case DSUBI: return cfg.intLatency;
            default: return cfg.intLatency;
        }
    }

    private void executeStep() {
        // For each station: if not executing and operands ready, start; else decrement remaining
        List<ReservationStation> all = new ArrayList<>();
        all.addAll(addStations); all.addAll(mulStations); all.addAll(intStations); all.addAll(loadBuffers);
        for (ReservationStation rs : all) {
            if (!rs.busy) continue;
            if (!rs.executing) {
                if (rs.qj == null && rs.qk == null) {
                    rs.executing = true;
                    history.add(rs.name + " starts executing " + rs.inst);
                }
            }
            if (rs.executing && rs.remaining > 0) {
                rs.remaining--;
                if (rs.remaining == 0) {
                    rs.writebackPending = true;
                    history.add(rs.name + " finished execution of " + rs.inst);
                }
            }
        }
    }

    private void writebackStep() {
        // publish at most one result per cycle (simplified). Choose oldest writebackPending by station name order.
        List<ReservationStation> all = new ArrayList<>();
        all.addAll(addStations); all.addAll(mulStations); all.addAll(intStations); all.addAll(loadBuffers);
        ReservationStation ready = null;
        for (ReservationStation rs : all) if (rs.busy && rs.writebackPending) { ready = rs; break; }
        if (ready == null) return;

        // create result value (for demo use a deterministic number: cycle * 10 + hash)
        int value = cycle * 10 + Math.abs(Objects.hashCode(ready.inst.raw)) % 100;
        // if load: simulate cache access latency by deducting additional cycles in future - here just note it
        if (ready.inst.type == InstructionType.LD || ready.inst.type == InstructionType.LW || ready.inst.type == InstructionType.L_D || ready.inst.type == InstructionType.L_S) {
            int addr = (ready.inst.immediate == null ? 0 : ready.inst.immediate) + (ready.vj == null ? 0 : ready.vj);
            int accessLatency = cache.access(addr, 4);
            history.add("Cache access for " + ready.inst + " addr=" + addr + " latency=" + accessLatency);
        }

        // write to registers that have this station as tag
        // find dest
        if (ready.inst.dest != null) {
            registers.set(ready.inst.dest, value);
            registers.clearTag(ready.inst.dest, ready.name);
            history.add(ready.name + " writeback " + ready.inst.dest + "=" + value);
        }

        // update waiting stations
        for (ReservationStation rs : all) {
            if (!rs.busy) continue;
            if (ready.name.equals(rs.qj)) { rs.vj = value; rs.qj = null; }
            if (ready.name.equals(rs.qk)) { rs.vk = value; rs.qk = null; }
        }

        // clear station
        ready.clear();
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