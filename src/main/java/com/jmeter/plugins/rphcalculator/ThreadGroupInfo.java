package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.testelement.TestElement;

public class ThreadGroupInfo {

    private final TestElement threadGroup;
    private final String name;
    private int targetRph;
    private double iterationDurationSec;
    private int rampUpSec;
    private int holdSec; // Возвращаем для сохранения оригинального времени
    private int rampDownSec;
    private int httpSamplersCount;
    private int calculatedThreads;
    private double calculatedIntervalMs;
    private boolean hasTimer;

    public ThreadGroupInfo(TestElement tg, String name) {
        this.threadGroup = tg;
        this.name = name;
        this.targetRph = 0;
        this.iterationDurationSec = 10;
        this.rampUpSec = 30;
        this.holdSec = 3600; // Дефолтное значение
        this.rampDownSec = 30;
        this.httpSamplersCount = 0;
        this.calculatedThreads = 0;
        this.calculatedIntervalMs = 0;
        this.hasTimer = false;
    }

    public TestElement getThreadGroup() { return threadGroup; }
    public String getName() { return name; }
    public int getTargetRph() { return targetRph; }
    public void setTargetRph(int v) { this.targetRph = v; }
    public double getIterationDurationSec() { return iterationDurationSec; }
    public void setIterationDurationSec(double v) { this.iterationDurationSec = v; }
    public int getRampUpSec() { return rampUpSec; }
    public void setRampUpSec(int v) { this.rampUpSec = v; }
    public int getHoldSec() { return holdSec; }
    public void setHoldSec(int v) { this.holdSec = v; }
    public int getRampDownSec() { return rampDownSec; }
    public void setRampDownSec(int v) { this.rampDownSec = v; }
    public int getHttpSamplersCount() { return httpSamplersCount; }
    public void setHttpSamplersCount(int v) { this.httpSamplersCount = v; }
    public int getCalculatedThreads() { return calculatedThreads; }
    public void setCalculatedThreads(int v) { this.calculatedThreads = v; }
    public double getCalculatedIntervalMs() { return calculatedIntervalMs; }
    public void setCalculatedIntervalMs(double v) { this.calculatedIntervalMs = v; }
    public boolean isHasTimer() { return hasTimer; }
    public void setHasTimer(boolean v) { this.hasTimer = v; }
}