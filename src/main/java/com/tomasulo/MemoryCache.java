package com.tomasulo;

import java.util.*;

// Very simple direct-mapped cache model for data cache only.
public class MemoryCache {
    @SuppressWarnings("unused")
    private final int cacheSizeBytes;
    private final int blockSizeBytes;
    private final int lines;
    private final int hitLatency;
    private final int missPenalty;
    private final Map<Integer, Integer> memory = new HashMap<>(); // byte address to value

    private final CacheLine[] linesArr;
    private int hits = 0;
    private int misses = 0;

    public MemoryCache(int cacheSizeBytes, int blockSizeBytes, int hitLatency, int missPenalty) {
        this.cacheSizeBytes = cacheSizeBytes;
        this.blockSizeBytes = blockSizeBytes;
        this.hitLatency = hitLatency;
        this.missPenalty = missPenalty;
        this.lines = Math.max(1, cacheSizeBytes / blockSizeBytes);
        this.linesArr = new CacheLine[lines];
        for (int i = 0; i < lines; i++) linesArr[i] = new CacheLine();
        
        // Pre-initialize memory with test data
        initializeMemory();
    }
    
    private void initializeMemory() {
        // Initialize memory with meaningful test values at specific addresses
        // These addresses are commonly used in test cases
        
        // Address 1000-1007: Base address for R2 in test cases
        writeWord(1000, 10);   // Value 10 at address 1000 (for offset 0)
        writeWord(1004, 20);   // Value 20 at address 1004 (for offset 4)
        writeWord(1008, 30);   // Value 30 at address 1008 (for offset 8)
        writeWord(1012, 40);   // Value 40 at address 1012 (for offset 12)
        writeWord(1016, 50);   // Value 50 at address 1016 (for offset 16)
        writeWord(1020, 60);   // Value 60 at address 1020 (for offset 20)
        writeWord(1024, 70);   // Value 70 at address 1024 (for offset 24)
        
        // Address 2000-2007: Base address for alternative test cases
        writeWord(2000, 100);  // Value 100 at address 2000
        writeWord(2004, 200);  // Value 200 at address 2004
        writeWord(2008, 300);  // Value 300 at address 2008
        writeWord(2012, 400);  // Value 400 at address 2012
        writeWord(2016, 500);  // Value 500 at address 2016
        writeWord(2020, 600);  // Value 600 at address 2020
        
        // Address 0-32: For edge case testing with R2=0
        writeWord(0, 5);       // Value 5 at address 0
        writeWord(8, 15);      // Value 15 at address 8
        writeWord(16, 25);     // Value 25 at address 16
        writeWord(24, 35);     // Value 35 at address 24
    }

    // Simple direct-mapped index
    private int indexOf(int address) {
        int block = address / blockSizeBytes;
        return Math.floorMod(block, lines);
    }

    private int tagOf(int address) {
        return address / blockSizeBytes;
    }

    public int access(int address, int size) {
        // returns access latency (hitLatency or missPenalty + hitLatency)
        int idx = indexOf(address);
        int tag = tagOf(address);
        CacheLine line = linesArr[idx];
        if (line.valid && line.tag == tag) {
            hits++;
            return hitLatency;
        } else {
            misses++;
            // load block from memory (simulated): bring into cache
            line.valid = true;
            line.tag = tag;
            // fill bytes from memory (if absent default 0)
            int base = tag * blockSizeBytes;
            for (int i = 0; i < blockSizeBytes; i++) {
                memory.putIfAbsent(base + i, 0);
            }
            return missPenalty + hitLatency;
        }
    }

    public int readWord(int address) {
        // word = 4 bytes little-endian combined
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (memory.getOrDefault(address + i, 0) & 0xFF) << (8 * i);
        }
        return v;
    }

    public void writeWord(int address, int value) {
        // Write word to memory and update cache if block is present
        for (int i = 0; i < 4; i++) {
            memory.put(address + i, (value >> (8 * i)) & 0xFF);
        }
        // Mark cache line as valid with updated data
        int idx = indexOf(address);
        int tag = tagOf(address);
        CacheLine line = linesArr[idx];
        if (line.valid && line.tag == tag) {
            // Cache hit on write - data is already in cache
        } else {
            // Write-allocate: bring block into cache
            line.valid = true;
            line.tag = tag;
        }
    }
    
    public int getHits() { return hits; }
    public int getMisses() { return misses; }
    public int getLines() { return lines; }
    public int getBlockSize() { return blockSizeBytes; }

    private static class CacheLine {
        boolean valid = false;
        int tag = -1;
    }
}