package com.tomasulo;

public class Instruction {
    public final InstructionType type;
    public final String dest; // register or memory
    public final String src1;
    public final String src2;
    public final Integer immediate; // for loads/stores/branches
    public final String raw; // original textual

    public Instruction(InstructionType type, String dest, String src1, String src2, Integer immediate, String raw) {
        this.type = type;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.immediate = immediate;
        this.raw = raw;
    }

    public static Instruction nop() {
        return new Instruction(InstructionType.NOP, null, null, null, null, "NOP");
    }

    @Override
    public String toString() {
        return raw == null ? type.name() : raw;
    }
}