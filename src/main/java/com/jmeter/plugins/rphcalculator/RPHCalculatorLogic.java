package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.sampler.TestAction;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.DoubleProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.timers.ConstantThroughputTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class RPHCalculatorLogic {

    private static final Logger log = LoggerFactory.getLogger(RPHCalculatorLogic.class);
    private static final String ULTIMATE_TG_CLASS = "kg.apc.jmeter.threads.UltimateThreadGroup";
    private static final String ULTIMATE_TG_PROP = "ultimatethreadgroupdata";
    private static final String PACING_ACTION_NAME = "Pacing Action";
    private static final String PACING_TIMER_NAME = "Pacing Timer";

    public static void calculateForward(ThreadGroupInfo info, JTextArea logArea, GuiPackage guiPackage, int holdSec) {
        TestElement tg = info.getThreadGroup();
        int httpCount = info.getHttpSamplersCount();
        if (httpCount <= 0) {
            logArea.append("WARNING: No HTTP samplers found in '" + info.getName() + "'. Assuming 1.\n");
            httpCount = 1;
            info.setHttpSamplersCount(1);
        }

        double rpm = (double) info.getTargetRph() / 60.0;
        double requiredThreads = (rpm * (info.getIterationDurationSec() / 60.0));
        int threads = Math.max(1, (int) Math.ceil(requiredThreads));

        info.setCalculatedThreads(threads);

        updateThreadGroupProperties(tg, info, threads, holdSec, logArea);

        logArea.append(String.format("'%s': %d RPH (%.1f RPM) → %d threads\n",
                info.getName(), info.getTargetRph(), rpm, threads));

        addOrUpdatePacingElements(info, tg, logArea, guiPackage, rpm);
    }

    public static void generateStepUpSchedule(ThreadGroupInfo info, JTextArea logArea, GuiPackage guiPackage,
                                              int steps, int stepDuration, double initialLoadPct, double incrementPct, int stepRampUp) {
        TestElement tg = info.getThreadGroup();
        if (!tg.getClass().getName().equals(ULTIMATE_TG_CLASS)) {
            logArea.append("ERROR: '" + info.getName() + "' is not an Ultimate Thread Group. Step-up requires Ultimate Thread Group.\n");
            return;
        }

        double baseRph = info.getTargetRph();
        if (baseRph <= 0) {
            logArea.append("WARNING: Base Target RPH is 0 or less for '" + info.getName() + "'. Cannot generate Step-up.\n");
            return;
        }

        JMeterProperty scheduleProp = tg.getProperty(ULTIMATE_TG_PROP);
        if (!(scheduleProp instanceof CollectionProperty)) {
            logArea.append("ERROR: Cannot find or access schedule for '" + info.getName() + "'.\n");
            return;
        }
        CollectionProperty schedule = (CollectionProperty) scheduleProp;
        schedule.clear();

        double maxRph = 0;
        int previousThreads = 0;

        for (int i = 0; i < steps; i++) {
            double currentLoadPct = initialLoadPct + (i * incrementPct);
            double currentRph = baseRph * (currentLoadPct / 100.0);
            maxRph = Math.max(maxRph, currentRph);

            double currentRpm = currentRph / 60.0;
            double requiredThreads = currentRpm * (info.getIterationDurationSec() / 60.0);
            int currentTotalThreads = Math.max(1, (int) Math.ceil(requiredThreads));

            int threadsToAdd = currentTotalThreads - previousThreads;

            if (threadsToAdd > 0 || i == 0) {
                if (i == 0 && threadsToAdd <= 0) threadsToAdd = 1;

                int startDelay = i * stepDuration;
                int activeTime = (steps * stepDuration) - startDelay;
                int holdTime = activeTime - stepRampUp;
                if (holdTime < 0) holdTime = 0;

                CollectionProperty newRow = new CollectionProperty();
                newRow.setName(String.valueOf(i));
                newRow.addProperty(new StringProperty("0", String.valueOf(threadsToAdd)));
                newRow.addProperty(new StringProperty("1", String.valueOf(startDelay)));
                newRow.addProperty(new StringProperty("2", String.valueOf(stepRampUp)));
                newRow.addProperty(new StringProperty("3", String.valueOf(holdTime)));
                newRow.addProperty(new StringProperty("4", String.valueOf(info.getRampDownSec())));

                schedule.addProperty(newRow);
            }
            previousThreads = currentTotalThreads;
        }

        info.setCalculatedThreads(previousThreads);
        addOrUpdatePacingElements(info, tg, logArea, guiPackage, maxRph / 60.0);

        logArea.append(String.format("'%s': Step-up applied. Max %.0f%% (%.0f RPH) → Max %d threads.\n",
                info.getName(), initialLoadPct + ((steps - 1) * incrementPct), maxRph, previousThreads));
    }

    private static void updateThreadGroupProperties(TestElement tg, ThreadGroupInfo info, int threads, int holdSec, JTextArea logArea) {
        String className = tg.getClass().getName();
        if (className.equals(ULTIMATE_TG_CLASS)) {
            JMeterProperty scheduleProp = tg.getProperty(ULTIMATE_TG_PROP);
            if (!(scheduleProp instanceof CollectionProperty)) {
                logArea.append("ERROR: Cannot find or access schedule for '" + info.getName() + "'.\n");
                return;
            }
            CollectionProperty schedule = (CollectionProperty) scheduleProp;
            schedule.clear();

            CollectionProperty newRow = new CollectionProperty();
            newRow.setName("0");
            newRow.addProperty(new StringProperty("0", String.valueOf(threads)));
            newRow.addProperty(new StringProperty("1", "0"));
            newRow.addProperty(new StringProperty("2", String.valueOf(info.getRampUpSec())));
            newRow.addProperty(new StringProperty("3", String.valueOf(holdSec)));
            newRow.addProperty(new StringProperty("4", String.valueOf(info.getRampDownSec())));

            schedule.addProperty(newRow);
        } else if (tg instanceof ThreadGroup) {
            tg.setProperty(ThreadGroup.NUM_THREADS, String.valueOf(threads));
            tg.setProperty(ThreadGroup.RAMP_TIME, String.valueOf(info.getRampUpSec()));
            tg.setProperty(ThreadGroup.SCHEDULER, true);
            tg.setProperty(ThreadGroup.DURATION, String.valueOf(holdSec));

            TestElement controller = ((AbstractThreadGroup) tg).getSamplerController();
            if (controller instanceof LoopController) {
                ((LoopController) controller).setLoops(-1);
            }
        }
    }

    private static ConstantThroughputTimer createNewPacingTimer(double rpm) {
        ConstantThroughputTimer timer = new ConstantThroughputTimer();
        timer.setProperty(TestElement.GUI_CLASS, TestBeanGUI.class.getName());
        timer.setProperty(TestElement.TEST_CLASS, ConstantThroughputTimer.class.getName());
        timer.setName(PACING_TIMER_NAME);
        timer.setProperty(ConstantThroughputTimer.CALC_MODE, 1);
        // Correct way to set a double property in JMeter
        timer.setProperty(new DoubleProperty(ConstantThroughputTimer.THROUGHPUT, rpm));
        timer.setEnabled(true);
        return timer;
    }

    private static void addOrUpdatePacingElements(ThreadGroupInfo info, TestElement tg, JTextArea logArea, GuiPackage guiPackage, double rpm) {
        JMeterTreeNode parentNode = guiPackage.getTreeModel().getNodeOf(tg);
        if (parentNode == null) {
            log.error("Could not find tree node for Thread Group: {}", tg.getName());
            return;
        }

        JMeterTreeNode pacingActionNode = findPacingActionNode(parentNode);
        ConstantThroughputTimer timer = null;

        if (pacingActionNode != null) {
            timer = findPacingTimerInNode(pacingActionNode);
        }

        try {
            if (timer != null) {
                // Correct way to set a double property in JMeter
                timer.setProperty(new DoubleProperty(ConstantThroughputTimer.THROUGHPUT, rpm));
                logArea.append("  Updated existing Pacing Timer.\n");
            } else if (pacingActionNode != null) {
                ConstantThroughputTimer newTimer = createNewPacingTimer(rpm);
                guiPackage.getTreeModel().addComponent(newTimer, pacingActionNode);
                logArea.append("  Added new Pacing Timer to existing Action.\n");
            } else {
                TestAction fca = new TestAction();
                fca.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.sampler.gui.TestActionGui");
                fca.setProperty(TestElement.TEST_CLASS, TestAction.class.getName());
                fca.setName(PACING_ACTION_NAME);
                fca.setAction(TestAction.PAUSE);
                fca.setDuration("0");
                fca.setEnabled(true);
                
                JMeterTreeNode fcaNode = guiPackage.getTreeModel().addComponent(fca, parentNode);

                ConstantThroughputTimer newTimer = createNewPacingTimer(rpm);
                guiPackage.getTreeModel().addComponent(newTimer, fcaNode);
                logArea.append("  Added new Pacing Action with Timer.\n");
            }
        } catch (IllegalUserActionException e) {
            log.error("Failed to add Pacing elements to GUI for '{}'", tg.getName(), e);
            logArea.append("  ERROR: Failed to add Pacing elements for '" + tg.getName() + "'.\n");
        }

        info.setHasTimer(true);
    }

    public static void calculateReverse(ThreadGroupInfo info, JTextArea logArea, GuiPackage guiPackage) {
        TestElement tg = info.getThreadGroup();
        restoreThreadGroupProperties(tg, info);

        ConstantThroughputTimer timer = findPacingTimer(tg, guiPackage);

        if (timer != null) {
            double rpm = timer.getPropertyAsDouble(ConstantThroughputTimer.THROUGHPUT);
            if (info.getCalculatedThreads() > 0 && info.getIterationDurationSec() > 0) {
                double rph = (double) info.getCalculatedThreads() * 60.0 / (info.getIterationDurationSec() / 60.0);
                info.setTargetRph((int) Math.round(rph));
            } else {
                info.setTargetRph(0);
            }
            info.setHasTimer(true);
            logArea.append(String.format("'%s': Reversed from threads. Current threads: %d → %d RPH\n",
                    info.getName(), info.getCalculatedThreads(), info.getTargetRph()));
        } else {
            info.setHasTimer(false);
            logArea.append("'" + info.getName() + "': No Pacing Timer found. Current threads: " + info.getCalculatedThreads() + "\n");
        }
    }

    private static void restoreThreadGroupProperties(TestElement tg, ThreadGroupInfo info) {
        String className = tg.getClass().getName();
        if (className.equals(ULTIMATE_TG_CLASS)) {
            JMeterProperty scheduleProp = tg.getProperty(ULTIMATE_TG_PROP);
            if (scheduleProp instanceof CollectionProperty) {
                CollectionProperty schedule = (CollectionProperty) scheduleProp;
                int totalThreads = 0;
                for (JMeterProperty prop : schedule) {
                    if (prop instanceof CollectionProperty) {
                        CollectionProperty row = (CollectionProperty) prop;
                        if (row.size() > 0) {
                            try {
                                totalThreads += Integer.parseInt(row.get(0).getStringValue());
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse thread count from schedule row in '{}'", tg.getName());
                            }
                        }
                    }
                }
                info.setCalculatedThreads(totalThreads);

                if (schedule.size() > 0 && schedule.get(0) instanceof CollectionProperty) {
                    CollectionProperty firstRow = (CollectionProperty) schedule.get(0);
                    if (firstRow.size() >= 5) {
                        try {
                            info.setRampUpSec(Integer.parseInt(firstRow.get(2).getStringValue()));
                            info.setHoldSec(Integer.parseInt(firstRow.get(3).getStringValue()));
                            info.setRampDownSec(Integer.parseInt(firstRow.get(4).getStringValue()));
                        } catch (NumberFormatException e) {
                            log.warn("Could not parse ramp-up/hold/ramp-down from schedule in '{}'", tg.getName());
                        }
                    }
                }
                return;
            }
            setDefaultsForUltimate(info);
        } else if (tg instanceof ThreadGroup) {
            info.setCalculatedThreads(tg.getPropertyAsInt(ThreadGroup.NUM_THREADS, 0));
            info.setRampUpSec(tg.getPropertyAsInt(ThreadGroup.RAMP_TIME, 30));
            if (tg.getPropertyAsBoolean(ThreadGroup.SCHEDULER)) {
                info.setHoldSec(tg.getPropertyAsInt(ThreadGroup.DURATION, 3600));
            }
            info.setRampDownSec(30);
        }
    }

    private static void setDefaultsForUltimate(ThreadGroupInfo info) {
        info.setCalculatedThreads(0);
        info.setHoldSec(3600);
        info.setRampDownSec(30);
        info.setRampUpSec(30);
    }

    private static JMeterTreeNode findPacingActionNode(JMeterTreeNode parentNode) {
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            JMeterTreeNode childNode = (JMeterTreeNode) parentNode.getChildAt(i);
            TestElement childElement = childNode.getTestElement();
            if (childElement instanceof TestAction && PACING_ACTION_NAME.equals(childElement.getName())) {
                return childNode;
            }
        }
        return null;
    }

    private static ConstantThroughputTimer findPacingTimerInNode(JMeterTreeNode pacingActionNode) {
        for (int i = 0; i < pacingActionNode.getChildCount(); i++) {
            JMeterTreeNode grandchildNode = (JMeterTreeNode) pacingActionNode.getChildAt(i);
            TestElement grandchildElement = grandchildNode.getTestElement();
            if (grandchildElement instanceof ConstantThroughputTimer && PACING_TIMER_NAME.equals(grandchildElement.getName())) {
                return (ConstantThroughputTimer) grandchildElement;
            }
        }
        return null;
    }

    private static ConstantThroughputTimer findPacingTimer(TestElement parent, GuiPackage guiPackage) {
        JMeterTreeNode parentNode = guiPackage.getTreeModel().getNodeOf(parent);
        if (parentNode != null) {
            JMeterTreeNode pacingActionNode = findPacingActionNode(parentNode);
            if (pacingActionNode != null) {
                return findPacingTimerInNode(pacingActionNode);
            }
        }
        return null;
    }

    public static int countHttpSamplers(TestElement parent, GuiPackage guiPackage) {
        int count = 0;
        JMeterTreeNode parentNode = guiPackage.getTreeModel().getNodeOf(parent);
        if (parentNode != null) {
            for (int i = 0; i < parentNode.getChildCount(); i++) {
                JMeterTreeNode childNode = (JMeterTreeNode) parentNode.getChildAt(i);
                if (childNode.getTestElement() instanceof HTTPSamplerProxy) {
                    count++;
                }
            }
        }
        return count;
    }
}