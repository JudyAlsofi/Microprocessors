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
        
        // Address 1000-1024: Base address for R2 in test cases
        writeWordToMemoryOnly(1000, 10);   // Value 10 at address 1000 (for offset 0)
        writeWordToMemoryOnly(1004, 20);   // Value 20 at address 1004 (for offset 4)
        writeWordToMemoryOnly(1008, 30);   // Value 30 at address 1008 (for offset 8)
        writeWordToMemoryOnly(1012, 40);   // Value 40 at address 1012 (for offset 12)
        writeWordToMemoryOnly(1016, 50);   // Value 50 at address 1016 (for offset 16)
        writeWordToMemoryOnly(1020, 60);   // Value 60 at address 1020 (for offset 20)
        writeWordToMemoryOnly(1024, 70);   // Value 70 at address 1024 (for offset 24)
        
        // Address 2000-2020: Base address for alternative test cases
        writeWordToMemoryOnly(2000, 100);  // Value 100 at address 2000
        writeWordToMemoryOnly(2004, 200);  // Value 200 at address 2004
        writeWordToMemoryOnly(2008, 300);  // Value 300 at address 2008
        writeWordToMemoryOnly(2012, 400);  // Value 400 at address 2012
        writeWordToMemoryOnly(2016, 500);  // Value 500 at address 2016
        writeWordToMemoryOnly(2020, 600);  // Value 600 at address 2020
        
        // Address 0-24: For edge case testing with R2=0
        writeWordToMemoryOnly(0, 5);       // Value 5 at address 0
        writeWordToMemoryOnly(8, 15);      // Value 15 at address 8
        writeWordToMemoryOnly(16, 25);     // Value 25 at address 16
        writeWordToMemoryOnly(24, 35);     // Value 35 at address 24
    }
    
    // Write to memory only, without updating cache (for initialization)
    private void writeWordToMemoryOnly(int address, int value) {
        for (int i = 0; i < 4; i++) {
            memory.put(address + i, (value >> (8 * i)) & 0xFF);
        }
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
        // Check hit/miss and return MISS PENALTY only (not including hit latency)
        // Hit latency is part of the load/store execution time
        // Cache will be updated only after miss penalty is paid
        int idx = indexOf(address);
        int tag = tagOf(address);
        CacheLine line = linesArr[idx];
        if (line.valid && line.tag == tag) {
            hits++;
            return 0; // Hit - no miss penalty, only execution time
        } else {
            misses++;
            // Don't update cache here - will be updated after miss penalty is paid
            return missPenalty; // Return only miss penalty, not including hit latency
        }
    }
    
    // Called after a load completes to bring the block into cache
    public void loadBlockIntoCache(int address) {
        int idx = indexOf(address);
        int tag = tagOf(address);
        CacheLine line = linesArr[idx];
        
        // Bring block into cache
        line.valid = true;
        line.tag = tag;
        
        // Fill bytes from memory (if absent default 0)
        int base = tag * blockSizeBytes;
        for (int i = 0; i < blockSizeBytes; i++) {
            memory.putIfAbsent(base + i, 0);
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
        // Write word to memory and update cache
        // Check if it's a hit or miss for statistics
        int idx = indexOf(address);
        int tag = tagOf(address);
        CacheLine line = linesArr[idx];
        
        if (line.valid && line.tag == tag) {
            // Cache hit on write
            hits++;
        } else {
            // Cache miss - write-allocate: bring block into cache
            misses++;
            line.valid = true;
            line.tag = tag;
        }
        
        // Write to memory
        for (int i = 0; i < 4; i++) {
            memory.put(address + i, (value >> (8 * i)) & 0xFF);
        }
    }
    
    public int getHits() { return hits; }
    public int getMisses() { return misses; }
    public int getLines() { return lines; }
    public int getBlockSize() { return blockSizeBytes; }
    
    // Get cache state for display
    public List<Map<String, Object>> getCacheState() {
        List<Map<String, Object>> state = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            Map<String, Object> lineInfo = new HashMap<>();
            lineInfo.put("index", i);
            lineInfo.put("valid", linesArr[i].valid);
            lineInfo.put("tag", linesArr[i].valid ? linesArr[i].tag : -1);
            // Calculate address range for this block
            if (linesArr[i].valid) {
                int baseAddr = linesArr[i].tag * blockSizeBytes;
                lineInfo.put("baseAddr", baseAddr);
                lineInfo.put("endAddr", baseAddr + blockSizeBytes - 1);
                // Get first few bytes as sample data
                StringBuilder data = new StringBuilder();
                for (int j = 0; j < Math.min(4, blockSizeBytes); j++) {
                    int byteVal = memory.getOrDefault(baseAddr + j, 0);
                    data.append(String.format("%02X ", byteVal));
                }
                lineInfo.put("data", data.toString().trim());
            } else {
                lineInfo.put("baseAddr", -1);
                lineInfo.put("endAddr", -1);
                lineInfo.put("data", "---");
            }
            state.add(lineInfo);
        }
        return state;
    }
    
    // Get memory state (non-zero words only) for display
    public List<Map<String, Object>> getMemoryState() {
        List<Map<String, Object>> state = new ArrayList<>();
        Set<Integer> wordAddresses = new TreeSet<>();
        
        // Find all word-aligned addresses that have non-zero data
        for (Integer byteAddr : memory.keySet()) {
            int wordAddr = (byteAddr / 4) * 4; // Align to word boundary
            wordAddresses.add(wordAddr);
        }
        
        // Read each word and add to state if non-zero
        for (Integer addr : wordAddresses) {
            int value = readWord(addr);
            if (value != 0) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("address", addr);
                entry.put("value", value);
                state.add(entry);
            }
        }
        
        return state;
    }

    private static class CacheLine {
        boolean valid = false;
        int tag = -1;
    }
}