package com.tomasulo;

import java.util.HashMap;
import java.util.Map;

public class RegisterFile {
    // Map register name -> value
    public final Map<String, Integer> regs = new HashMap<>();
    // Map register name -> reservation station tag that will produce it (for renaming)
    public final Map<String, String> tag = new HashMap<>();

    public RegisterFile() {
        // Initialize some registers for demo
        for (int i = 0; i < 32; i++) regs.put("R" + i, 0);
        for (int i = 0; i < 32; i++) regs.put("F" + i, 0);
    }

    public int get(String r) {
        return regs.getOrDefault(r, 0);
    }

    public void set(String r, int v) {
        regs.put(r, v);
    }

    public void setTag(String r, String station) {
        if (r == null) return;
        tag.put(r, station);
    }

    public String getTag(String r) {
        return tag.get(r);
    }

    public void clearTag(String r, String station) {
        String t = tag.get(r);
        if (t != null && t.equals(station)) tag.remove(r);
    }
}