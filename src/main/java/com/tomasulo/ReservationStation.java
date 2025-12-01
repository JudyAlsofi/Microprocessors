package com.tomasulo;

public class ReservationStation {
    public final String name;
    public boolean busy = false;
    public Instruction inst = null;
    public String qj = null; // tag of producer for src1
    public String qk = null; // tag for src2
    public Integer vj = null;
    public Integer vk = null;
    public int remaining = 0; // cycles remaining for execution
    public boolean executing = false;
    public boolean writebackPending = false;
    public Integer address = null; // effective address for loads/stores
    public boolean addressReady = false; // true when address is computed

    public ReservationStation(String name) {
        this.name = name;
    }

    public void clear() {
        busy = false;
        inst = null;
        qj = qk = null;
        vj = vk = null;
        remaining = 0;
        executing = false;
        writebackPending = false;
        address = null;
        addressReady = false;
    }
}