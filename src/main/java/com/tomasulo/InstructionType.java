package com.tomasulo;

public enum InstructionType {
    LD, LW, L_S, L_D, SD, SW, S_D, S_W,
    ADDI, SUBI, DADDI, DSUBI,
    ADD, SUB, MUL, DIV,
    ADD_D, SUB_D, MUL_D, DIV_D,
    BEQ, BNE,
    NOP
}