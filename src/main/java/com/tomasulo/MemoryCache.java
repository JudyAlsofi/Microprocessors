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