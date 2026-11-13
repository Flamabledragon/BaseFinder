package com.example.basefinder;

public class Config {
    public boolean enabled = true;
    public boolean notify = true;
    public int minScore = 15;        // cluster score needed to count as a "base"
    public int chunksPerTick = 4;    // scan speed
}
