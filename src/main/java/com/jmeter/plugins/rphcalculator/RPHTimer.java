package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.timers.Timer;
import org.apache.jmeter.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RPHTimer extends AbstractTestElement implements Timer {

    private static final Logger log = LoggerFactory.getLogger(RPHTimer.class);
    private static final long serialVersionUID = 1L;

    public static final String TARGET_RPH = "RPHTimer.targetRph";
    public static final String ITERATION_DURATION = "RPHTimer.iterationDuration";
    public static final String HTTP_SAMPLERS_COUNT = "RPHTimer.httpSamplersCount";
    public static final String INTERVAL_MS = "RPHTimer.intervalMs";
    public static final String DISTRIBUTION_MODE = "RPHTimer.distributionMode";

    public static final String MODE_CONSTANT = "constant";
    public static final String MODE_POISSON = "poisson";

    public RPHTimer() {
        setProperty(TARGET_RPH, "100");
        setProperty(ITERATION_DURATION, "10.0");
        setProperty(HTTP_SAMPLERS_COUNT, "1");
        setProperty(INTERVAL_MS, "36000.0");
        setProperty(DISTRIBUTION_MODE, MODE_CONSTANT);
    }

    // Getters and setters
    public int getTargetRph() { return getPropertyAsInt(TARGET_RPH, 100); }
    public void setTargetRph(int v) { setProperty(TARGET_RPH, String.valueOf(v)); }

    public double getIterationDuration() { return getPropertyAsDouble(ITERATION_DURATION); }
    public void setIterationDuration(double v) { setProperty(ITERATION_DURATION, String.valueOf(v)); }

    public int getHttpSamplersCount() { return getPropertyAsInt(HTTP_SAMPLERS_COUNT, 1); }
    public void setHttpSamplersCount(int v) { setProperty(HTTP_SAMPLERS_COUNT, String.valueOf(v)); }

    public double getIntervalMs() { return getPropertyAsDouble(INTERVAL_MS); }
    public void setIntervalMs(double v) { setProperty(INTERVAL_MS, String.valueOf(v)); }

    public String getDistributionMode() { return getPropertyAsString(DISTRIBUTION_MODE, MODE_CONSTANT); }
    public void setDistributionMode(String v) { setProperty(DISTRIBUTION_MODE, v); }

    @Override
    public long delay() {
        double interval = getIntervalMs();
        String mode = getDistributionMode();

        if (MODE_POISSON.equals(mode)) {
            return (long) (-Math.log(1 - Math.random()) * interval);
        } else {
            return (long) interval;
        }
    }
}