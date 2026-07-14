package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.ModuleController;
import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.sampler.TestAction;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.DoubleProperty;
import org.apache.jmeter.testelement.property.IntegerProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.LongProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.timers.ConstantThroughputTimer;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class RPHCalculatorLogic {

    private static final Logger log = LoggerFactory.getLogger(RPHCalculatorLogic.class);
    private static final String ULTIMATE_TG_CLASS = "kg.apc.jmeter.threads.UltimateThreadGroup";
    private static final String ULTIMATE_TG_PROP = "ultimatethreadgroupdata";
    private static final String PACING_ACTION_NAME = "Pacing Action";
    private static final String PACING_TIMER_NAME = "Pacing Timer";
    private static final String TARGET_RPH_VAR_PREFIX = "rph.target.";
    private static final String ITERATION_DUR_VAR_PREFIX = "rph.duration.";
    private static final String ACTUAL_RPH_VAR_PREFIX = "rph.actual.";
    private static final String THREADS_VAR_PREFIX = "rph.threads.";
    private static final String SAMPLERS_VAR_PREFIX = "rph.samplers.";


    public static void calculateForward(ThreadGroupInfo info, JTextArea logArea, GuiPackage guiPackage, int holdSec) {
        TestElement tg = info.getThreadGroup();
        
        if (info.getHttpSamplersCount() <= 0) {
            int actualSamplers = countHttpSamplers(tg, guiPackage);
            if (actualSamplers > 0) {
                info.setHttpSamplersCount(actualSamplers);
            }
        }
        
        softCalculate(info);

        int threads = info.getCalculatedThreads();
        int httpCount = Math.max(1, info.getHttpSamplersCount());

        double rpm = (double) info.getActualRph() / 60.0;

        updateThreadGroupProperties(tg, info, threads, holdSec, logArea);

        logArea.append(String.format("'%s': %d RPH (%.1f RPM) | %d samplers | %.1fs iter → %d threads\n",
                info.getName(), info.getActualRph(), rpm, httpCount, info.getIterationDurationSec(), threads));

        addOrUpdatePacingElements(info, tg, logArea, guiPackage, rpm);
    }

    public static void saveToVariables(ThreadGroupInfo info, GuiPackage guiPackage) {
        String baseName = info.getName().replaceAll("[^a-zA-Z0-9.-]", "_");

        JMeterTreeNode testPlanNode = (JMeterTreeNode) guiPackage.getTreeModel().getRoot();
        JMeterTreeNode udvNode = null;
        Arguments udv = null;

        for (int i = 0; i < testPlanNode.getChildCount(); i++) {
            JMeterTreeNode childNode = (JMeterTreeNode) testPlanNode.getChildAt(i);
            if (childNode.getTestElement() instanceof Arguments) {
                udvNode = childNode;
                udv = (Arguments) udvNode.getTestElement();
                break;
            }
        }

        if (udvNode == null) {
            List<JMeterTreeNode> udvNodes = guiPackage.getTreeModel().getNodesOfType(Arguments.class);
            if (!udvNodes.isEmpty()) {
                udvNode = udvNodes.get(0);
                udv = (Arguments) udvNode.getTestElement();
            }
        }

        if (udvNode == null) {
            udv = new Arguments();
            udv.setName("User Defined Variables (RPH Plugin)");
            udv.setProperty(TestElement.GUI_CLASS, ArgumentsPanel.class.getName());
            udv.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
            try {
                udvNode = guiPackage.getTreeModel().addComponent(udv, testPlanNode);
            } catch (IllegalUserActionException e) {
                log.error("Failed to create UDV for RPH Plugin", e);
                return;
            }
        }

        setUdvValue(udv, TARGET_RPH_VAR_PREFIX + baseName, String.valueOf(info.getTargetRph()));
        setUdvValue(udv, ACTUAL_RPH_VAR_PREFIX + baseName, String.valueOf(info.getActualRph()));
        setUdvValue(udv, ITERATION_DUR_VAR_PREFIX + baseName, String.valueOf(info.getIterationDurationSec()));
        setUdvValue(udv, THREADS_VAR_PREFIX + baseName, String.valueOf(info.getCalculatedThreads()));
        setUdvValue(udv, SAMPLERS_VAR_PREFIX + baseName, String.valueOf(info.getHttpSamplersCount()));

        guiPackage.getTreeModel().nodeChanged(udvNode);
    }

    private static void setUdvValue(Arguments udv, String key, String value) {
        udv.removeArgument(key);
        udv.addArgument(key, value);
    }

    private static int loadVariableAsInt(String prefix, String tgName, GuiPackage guiPackage) {
        String varName = prefix + tgName.replaceAll("[^a-zA-Z0-9.-]", "_");
        List<JMeterTreeNode> udvNodes = guiPackage.getTreeModel().getNodesOfType(Arguments.class);
        for (JMeterTreeNode node : udvNodes) {
            Arguments udv = (Arguments) node.getTestElement();
            String value = udv.getArgumentsAsMap().get(varName);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse integer from variable {}", varName);
                }
            }
        }
        return -1;
    }

    private static double loadVariableAsDouble(String prefix, String tgName, GuiPackage guiPackage) {
        String varName = prefix + tgName.replaceAll("[^a-zA-Z0-9.-]", "_");
        List<JMeterTreeNode> udvNodes = guiPackage.getTreeModel().getNodesOfType(Arguments.class);
        for (JMeterTreeNode node : udvNodes) {
            Arguments udv = (Arguments) node.getTestElement();
            String value = udv.getArgumentsAsMap().get(varName);
            if (value != null) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse double from variable {}", varName);
                }
            }
        }
        return -1.0;
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
            logArea.append("WARNING: Target RPH is 0 or less for '" + info.getName() + "'. Cannot generate Step-up.\n");
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
        int rowsAdded = 0;

        for (int i = 0; i < steps; i++) {
            double currentLoadPct = initialLoadPct + (i * incrementPct);
            double currentRph = baseRph * (currentLoadPct / 100.0);
            maxRph = Math.max(maxRph, currentRph);

            double currentRpm = currentRph / 60.0;
            int httpCount = Math.max(1, info.getHttpSamplersCount());
            double requiredThreads = (currentRpm * (info.getIterationDurationSec() / 60.0)) / httpCount;
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
                rowsAdded++;
            }
            previousThreads = currentTotalThreads;
        }

        info.setCalculatedThreads(previousThreads);
        info.setActualRph((int) Math.round(maxRph));
        info.setCalculatedIntervalMs(3600000.0 / Math.max(1, Math.round(maxRph)));

        double maxRpm = maxRph / 60.0;
        addOrUpdatePacingElements(info, tg, logArea, guiPackage, maxRpm);

        logArea.append(String.format("'%s': Step-up applied. Max %.0f RPH → Max %d threads. Rows: %d/%d\n",
                info.getName(), maxRph, previousThreads, rowsAdded, steps));
    }

    public static void syncThreadGroupTimings(ThreadGroupInfo info, JTextArea logArea) {
        TestElement tg = info.getThreadGroup();
        String className = tg.getClass().getName();
        int threads = info.getCalculatedThreads();
        
        if (className.equals(ULTIMATE_TG_CLASS)) {
            JMeterProperty scheduleProp = tg.getProperty(ULTIMATE_TG_PROP);
            if (scheduleProp instanceof CollectionProperty) {
                CollectionProperty schedule = (CollectionProperty) scheduleProp;
                if (schedule.size() > 0 && schedule.get(0) instanceof CollectionProperty) {
                    CollectionProperty firstRow = (CollectionProperty) schedule.get(0);
                    if (firstRow.size() >= 5) {
                        firstRow.set(0, new StringProperty("0", String.valueOf(threads)));
                        firstRow.set(2, new StringProperty("2", String.valueOf(info.getRampUpSec())));
                        firstRow.set(3, new StringProperty("3", String.valueOf(info.getHoldSec())));
                        firstRow.set(4, new StringProperty("4", String.valueOf(info.getRampDownSec())));
                    }
                }
            }
        } else if (tg instanceof ThreadGroup) {
            tg.setProperty(new IntegerProperty(ThreadGroup.NUM_THREADS, threads));
            tg.setProperty(new IntegerProperty(ThreadGroup.RAMP_TIME, info.getRampUpSec()));
            tg.setProperty(new BooleanProperty(ThreadGroup.SCHEDULER, true));
            tg.setProperty(new LongProperty(ThreadGroup.DURATION, (long) info.getHoldSec()));
        }
    }

    public static void softCalculate(ThreadGroupInfo info) {
        double rpm = (double) info.getActualRph() / 60.0;
        int httpCount = Math.max(1, info.getHttpSamplersCount());
        double iterMin = info.getIterationDurationSec() / 60.0;
        
        double requiredThreads = (rpm * iterMin) / httpCount;
        info.setCalculatedThreads(Math.max(1, (int) Math.ceil(requiredThreads)));
        info.setCalculatedIntervalMs(3600000.0 / Math.max(1, info.getActualRph()));
    }

    private static void updateThreadGroupProperties(TestElement tg, ThreadGroupInfo info, int threads, int holdSec, JTextArea logArea) {
        String className = tg.getClass().getName();
        if (className.equals(ULTIMATE_TG_CLASS)) {
            JMeterProperty scheduleProp = tg.getProperty(ULTIMATE_TG_PROP);
            CollectionProperty schedule;
            if (!(scheduleProp instanceof CollectionProperty)) {
                log.info("Creating new schedule property for '{}'", info.getName());
                schedule = new CollectionProperty(ULTIMATE_TG_PROP, new ArrayList<>());
                tg.setProperty(schedule);
            } else {
                schedule = (CollectionProperty) scheduleProp;
            }
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
            tg.setProperty(new IntegerProperty(ThreadGroup.NUM_THREADS, threads));
            tg.setProperty(new IntegerProperty(ThreadGroup.RAMP_TIME, info.getRampUpSec()));
            tg.setProperty(new BooleanProperty(ThreadGroup.SCHEDULER, true));
            tg.setProperty(new LongProperty(ThreadGroup.DURATION, (long) holdSec));

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
        timer.setProperty(new IntegerProperty(ConstantThroughputTimer.CALC_MODE, 4));
        timer.setProperty(new DoubleProperty(ConstantThroughputTimer.THROUGHPUT, rpm));
        timer.setEnabled(true);
        return timer;
    }

    public static void addOrUpdatePacingElements(ThreadGroupInfo info, TestElement tg, JTextArea logArea, GuiPackage guiPackage, double rpm) {
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

        guiPackage.getTreeModel().nodeChanged(parentNode);
        if (guiPackage.getCurrentNode() == parentNode) {
            guiPackage.getCurrentGui().configure(tg);
        }
        guiPackage.getMainFrame().repaint();
    }

    public static void calculateReverse(ThreadGroupInfo info, JTextArea logArea, GuiPackage guiPackage) {
        TestElement tg = info.getThreadGroup();
        restoreThreadGroupProperties(tg, info);

        // Load all saved values from User Defined Variables
        int storedTarget = loadVariableAsInt(TARGET_RPH_VAR_PREFIX, info.getName(), guiPackage);
        if (storedTarget != -1) info.setTargetRph(storedTarget);

        int storedActual = loadVariableAsInt(ACTUAL_RPH_VAR_PREFIX, info.getName(), guiPackage);
        if (storedActual != -1) info.setActualRph(storedActual);

        double storedDur = loadVariableAsDouble(ITERATION_DUR_VAR_PREFIX, info.getName(), guiPackage);
        if (storedDur != -1.0) info.setIterationDurationSec(storedDur);

        int storedThreads = loadVariableAsInt(THREADS_VAR_PREFIX, info.getName(), guiPackage);
        if (storedThreads != -1) info.setCalculatedThreads(storedThreads);

        int storedSamplers = loadVariableAsInt(SAMPLERS_VAR_PREFIX, info.getName(), guiPackage);
        if (storedSamplers != -1) info.setHttpSamplersCount(storedSamplers);
        
        if (info.getHttpSamplersCount() <= 0) {
            info.setHttpSamplersCount(countHttpSamplers(tg, guiPackage));
        }

        // If we have a stored Actual RPH, use it directly — don't re-derive from timer
        if (info.getActualRph() > 0) {
            info.setCalculatedIntervalMs(3600000.0 / info.getActualRph());
            ConstantThroughputTimer timer = findPacingTimer(tg, guiPackage);
            info.setHasTimer(timer != null);
            logArea.append(String.format("'%s': Loaded from variables. Target=%d, Actual=%d RPH\n",
                    info.getName(), info.getTargetRph(), info.getActualRph()));
            return;
        }

        // Fallback: derive from pacing timer (first time, no saved values yet)
        ConstantThroughputTimer timer = findPacingTimer(tg, guiPackage);

        if (timer != null) {
            double rpm = timer.getPropertyAsDouble(ConstantThroughputTimer.THROUGHPUT);
            int mode = timer.getPropertyAsInt(ConstantThroughputTimer.CALC_MODE, 0);
            
            double totalRph;
            if (mode >= 2 && mode <= 4) {
                totalRph = rpm * 60;
            } else {
                totalRph = rpm * 60 * info.getCalculatedThreads();
            }
            
            int rph = (int) Math.round(totalRph);
            
            if (info.getTargetRph() <= 0) info.setTargetRph(rph);
            info.setActualRph(rph);
            info.setCalculatedIntervalMs(3600000.0 / Math.max(1, rph));
            info.setHasTimer(true);
            logArea.append(String.format("'%s': Reversed from Pacing Timer. Current: %d RPH (%.1f RPM)\n",
                    info.getName(), info.getActualRph(), rpm));
        } else {
            if (info.getCalculatedThreads() > 0 && info.getIterationDurationSec() > 0) {
                int calcHttpCount = Math.max(1, info.getHttpSamplersCount());
                double maxRph = (double) info.getCalculatedThreads() * 3600.0 * calcHttpCount / info.getIterationDurationSec();
                int rph = (int) Math.round(maxRph);
                if (info.getTargetRph() <= 0) info.setTargetRph(rph);
                info.setActualRph(rph);
                info.setCalculatedIntervalMs(3600000.0 / Math.max(1, rph));
            } else {
                info.setActualRph(0);
            }
            info.setHasTimer(false);
            logArea.append("'" + info.getName() + "': No Pacing Timer found. Max capacity: " + info.getActualRph() + " RPH\n");
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
        JMeterTreeNode parentNode = guiPackage.getTreeModel().getNodeOf(parent);
        if (parentNode == null) return 0;
        return countHttpSamplersInNode(parentNode);
    }

    private static int countHttpSamplersInNode(JMeterTreeNode node) {
        int count = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            JMeterTreeNode child = (JMeterTreeNode) node.getChildAt(i);
            TestElement te = child.getTestElement();
            
            if (!te.isEnabled()) {
                continue;
            }

            if (te instanceof HTTPSamplerProxy) {
                count++;
            } else if (te instanceof ModuleController) {
                ModuleController mc = (ModuleController) te;
                JMeterTreeNode targetNode = (JMeterTreeNode) mc.getSelectedNode();
                if (targetNode != null) {
                    count += countHttpSamplersInNode(targetNode);
                }
            }

            count += countHttpSamplersInNode(child);
        }
        return count;
    }
}
