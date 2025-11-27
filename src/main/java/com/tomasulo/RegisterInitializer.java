package com.tomasulo;

// Helper to preload register values for testing
public class RegisterInitializer {
    public static void initializeForTestCase1(RegisterFile rf) {
        // Test Case 1 setup
        rf.set("R2", 100);  // Base address for loads/stores
        rf.set("F4", 5);    // Multiplier operand
        rf.set("F1", 10);
        rf.set("F3", 20);
    }
    
    public static void initializeForTestCase2(RegisterFile rf) {
        // Test Case 2 setup
        rf.set("R2", 200);
        rf.set("F1", 3);
        rf.set("F3", 7);
        rf.set("F4", 2);
    }
    
    public static void initializeForTestCase3(RegisterFile rf) {
        // Test Case 3 (loop) setup
        rf.set("R1", 0);   // Will be set to 24 by first DADDI
        rf.set("R2", 0);   // Loop termination value
        rf.set("F2", 3);   // Multiplier
    }
}
