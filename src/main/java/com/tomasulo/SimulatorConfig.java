package com.tomasulo;

public class SimulatorConfig {
    public int addLatency = 2;
    public int mulLatency = 5;
    public int divLatency = 10;
    public int loadLatency = 2; // cache hit
    public int storeLatency = 2; //cache hit
    public int intLatency = 1;

    // Cache
    public int cacheSizeBytes = 1024;
    public int blockSizeBytes = 16;
    public int cacheHitLatency = 2;
    public int cacheMissPenalty = 10;

    // sizes
    public int numAddStations = 3;
    public int numMulStations = 2;
    public int numLoadBuffers = 3;
    public int numIntStations = 2;
}
