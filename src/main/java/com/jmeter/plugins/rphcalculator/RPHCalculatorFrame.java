package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RPHCalculatorFrame extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(RPHCalculatorFrame.class);

    private final GuiPackage guiPackage;
    private final List<ThreadGroupInfo> threadGroupInfos = new ArrayList<>();
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextArea logArea;

    // UI components
    private JTextField loadPercentageField, durationField;
    private JTextField numStepsField, stepDurationField, initialLoadField, incrementField, stepRampUpField;

    // Properties for saving state
    private static final String PROP_STABILITY_LOAD = "rph_plugin.stability.load";
    private static final String PROP_STABILITY_DUR = "rph_plugin.stability.duration";
    private static final String PROP_STEPUP_STEPS = "rph_plugin.stepup.steps";
    private static final String PROP_STEPUP_DUR = "rph_plugin.stepup.duration";
    private static final String PROP_STEPUP_INIT = "rph_plugin.stepup.initial";
    private static final String PROP_STEPUP_INC = "rph_plugin.stepup.increment";
    private static final String PROP_STEPUP_RAMP = "rph_plugin.stepup.rampup";

    private static final String[] COLUMN_NAMES = {
            "Thread Group", "Target RPH", "Actual RPH", "Iteration Duration (sec)",
            "Ramp-up (sec)", "Hold (sec)", "Ramp-down (sec)",
            "HTTP Samplers", "Threads", "Interval (ms)"
    };

    public RPHCalculatorFrame(GuiPackage guiPackage) {
        this.guiPackage = guiPackage;
        setTitle("RPH Test Plan Generator");
        setSize(1200, 700);
        setLocationRelativeTo(guiPackage.getMainFrame());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        initComponents();
        loadSettings();
        refreshTable();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveSettings();
            }
        });
    }

    private String getVersionInfo() {
        try (java.io.InputStream is = getClass().getResourceAsStream("/version.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String ver = props.getProperty("version", "1.0.0");
                String date = props.getProperty("build.date", "");
                return ver + (date.isEmpty() ? "" : " (" + date + ")");
            }
        } catch (Exception e) {
            log.error("Failed to load version", e);
        }
        return "1.0.0";
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel(" RPH Calculator & Thread Group Configurator");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel versionLabel = new JLabel("v" + getVersionInfo() + " ");
        versionLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        versionLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(versionLabel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainPanel.add(createTableAndControlsPanel(), BorderLayout.CENTER);

        logArea = new JTextArea(6, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        mainPanel.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createTableAndControlsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Table
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return col > 0 && col < COLUMN_NAMES.length - 1;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(25);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons row
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JButton saveOnlyButton = new JButton("Save & Sync");
        saveOnlyButton.setToolTipText("Save Target RPH, Actual RPH, Iteration Duration to UDV and apply ramp-up/hold timings");
        saveOnlyButton.addActionListener(e -> saveSettingsOnly());
        controlPanel.add(saveOnlyButton);

        JButton refreshButton = new JButton("Full Refresh");
        refreshButton.setToolTipText("Reload all Thread Groups from the test plan and UDV");
        refreshButton.addActionListener(e -> refreshTable());
        controlPanel.add(refreshButton);

        JButton refreshSamplersButton = new JButton("Refresh Samplers");
        refreshSamplersButton.setToolTipText("Recalculate HTTP samplers for selected rows");
        refreshSamplersButton.addActionListener(e -> refreshSamplers());
        controlPanel.add(refreshSamplersButton);

        panel.add(controlPanel, BorderLayout.NORTH);

        // Stability + Step-up side by side
        JPanel generatorsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        generatorsPanel.add(createStabilityPanel());
        generatorsPanel.add(createStepUpPanel());
        panel.add(generatorsPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStabilityPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Stability Test"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Load % of Base:"), gbc);
        gbc.gridx = 1;
        loadPercentageField = new JTextField(8);
        panel.add(loadPercentageField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Duration (sec):"), gbc);
        gbc.gridx = 3;
        durationField = new JTextField(8);
        panel.add(durationField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 4; gbc.anchor = GridBagConstraints.CENTER;
        JButton generateStabilityButton = new JButton("Generate & Apply Stability Test");
        generateStabilityButton.addActionListener(e -> generateStabilityTest());
        panel.add(generateStabilityButton, gbc);

        return panel;
    }

    private JPanel createStepUpPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Step-up (Max Search)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Steps:"), gbc);
        gbc.gridx = 1;
        numStepsField = new JTextField(5);
        panel.add(numStepsField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Step Dur (sec):"), gbc);
        gbc.gridx = 3;
        stepDurationField = new JTextField(5);
        panel.add(stepDurationField, gbc);

        gbc.gridx = 4;
        panel.add(new JLabel("Start %:"), gbc);
        gbc.gridx = 5;
        initialLoadField = new JTextField(5);
        panel.add(initialLoadField, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Increment %:"), gbc);
        gbc.gridx = 1;
        incrementField = new JTextField(5);
        panel.add(incrementField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("Ramp-up (sec):"), gbc);
        gbc.gridx = 3;
        stepRampUpField = new JTextField(5);
        panel.add(stepRampUpField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 6; gbc.anchor = GridBagConstraints.CENTER;
        JButton generateStepUpButton = new JButton("Generate & Apply Step-up Test");
        generateStepUpButton.addActionListener(e -> generateStepUpTest());
        panel.add(generateStepUpButton, gbc);

        return panel;
    }

    private void saveSettingsOnly() {
        guiPackage.updateCurrentNode();
        int[] selectedRows = getSelectedRowsWithWarning();
        if (selectedRows.length == 0) return;

        logArea.setText("Saving settings for selected Thread Groups...\n");
        int successCount = 0;
        for (int rowIdx : selectedRows) {
            ThreadGroupInfo info = threadGroupInfos.get(rowIdx);
            try {
                updateInfoFromTable(info, rowIdx);
                boolean saved = RPHCalculatorLogic.saveToVariables(info, guiPackage, logArea);
                    if (!saved) continue;
                RPHCalculatorLogic.syncThreadGroupTimings(info, logArea);
                successCount++;
                logArea.append("'" + info.getName() + "': Saved. Target=" + info.getTargetRph()
                        + ", Actual=" + info.getActualRph() + ", Duration=" + info.getIterationDurationSec() + "s\n");
            } catch (Exception e) {
                handleRowError(rowIdx, info.getName(), e);
            }
        }
        postActionUpdate(successCount);
    }

    private void refreshSamplers() {
        guiPackage.updateCurrentNode();
        int[] selectedRows = getSelectedRowsWithWarning();
        if (selectedRows.length == 0) return;

        logArea.setText("Recalculating samplers for selected Thread Groups...\n");
        for (int rowIdx : selectedRows) {
            ThreadGroupInfo info = threadGroupInfos.get(rowIdx);
            int oldCount = info.getHttpSamplersCount();
            int newCount = RPHCalculatorLogic.countHttpSamplers(info.getThreadGroup(), guiPackage);
            info.setHttpSamplersCount(newCount);
            updateTableFromInfo(info, rowIdx);
            logArea.append(String.format("'%s': Sampler count updated from %d to %d.\n", info.getName(), oldCount, newCount));
        }
    }

    private void refreshTable() {
        guiPackage.updateCurrentNode();

        threadGroupInfos.clear();
        tableModel.setRowCount(0);
        logArea.setText("");

        List<TestElement> groups = guiPackage.getTreeModel().getNodesOfType(AbstractThreadGroup.class)
                .stream().map(JMeterTreeNode::getTestElement).collect(Collectors.toList());

        if (groups.isEmpty()) {
            logArea.append("No Thread Groups found in test plan.\n");
            return;
        }

        for (TestElement tg : groups) {
            ThreadGroupInfo info = new ThreadGroupInfo(tg, tg.getName());
            RPHCalculatorLogic.calculateReverse(info, logArea, guiPackage);

            threadGroupInfos.add(info);
            tableModel.addRow(new Object[]{
                    info.getName(), info.getTargetRph(), info.getActualRph(), info.getIterationDurationSec(),
                    info.getRampUpSec(), info.getHoldSec(), info.getRampDownSec(),
                    info.getHttpSamplersCount(), info.getCalculatedThreads(), "—"
            });
        }
        logArea.append("Found " + groups.size() + " Thread Group(s).\n");
    }

    private void generateStabilityTest() {
        guiPackage.updateCurrentNode();
        int[] selectedRows = getSelectedRowsWithWarning();
        if (selectedRows.length == 0) return;

        logArea.setText("Generating Stability Test...\n");
        int successCount = 0;
        try {
            double loadPct = Double.parseDouble(loadPercentageField.getText());
            int duration = Integer.parseInt(durationField.getText());

            for (int rowIdx : selectedRows) {
                ThreadGroupInfo info = threadGroupInfos.get(rowIdx);
                try {
                    updateInfoFromTable(info, rowIdx);

                    int originalTarget = info.getTargetRph();
                    int baseForCalculation = originalTarget > 0 ? originalTarget : info.getActualRph();

                    int stabilityTargetRph = (int) Math.round(baseForCalculation * (loadPct / 100.0));
                    info.setActualRph(stabilityTargetRph);

                    RPHCalculatorLogic.calculateForward(info, logArea, guiPackage, duration);
                    boolean saved = RPHCalculatorLogic.saveToVariables(info, guiPackage, logArea);
                    if (!saved) continue;

                    info.setTargetRph(originalTarget);
                    info.setActualRph(stabilityTargetRph);
                    info.setHoldSec(duration);

                    updateTableFromInfo(info, rowIdx);
                    successCount++;
                } catch (Exception e) {
                    handleRowError(rowIdx, info.getName(), e);
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number in settings.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
        postActionUpdate(successCount);
    }

    private void generateStepUpTest() {
        guiPackage.updateCurrentNode();
        int[] selectedRows = getSelectedRowsWithWarning();
        if (selectedRows.length == 0) return;

        logArea.setText("Generating Step-up Test...\n");
        int successCount = 0;
        try {
            int steps = Integer.parseInt(numStepsField.getText());
            int stepDuration = Integer.parseInt(stepDurationField.getText());
            double initialLoad = Double.parseDouble(initialLoadField.getText());
            double increment = Double.parseDouble(incrementField.getText());
            int stepRampUp = Integer.parseInt(stepRampUpField.getText());

            for (int rowIdx : selectedRows) {
                ThreadGroupInfo info = threadGroupInfos.get(rowIdx);
                try {
                    updateInfoFromTable(info, rowIdx);
                    RPHCalculatorLogic.generateStepUpSchedule(info, logArea, guiPackage, steps, stepDuration, initialLoad, increment, stepRampUp);
                    boolean saved = RPHCalculatorLogic.saveToVariables(info, guiPackage, logArea);
                    if (!saved) continue;
                    updateTableFromInfo(info, rowIdx);
                    successCount++;
                } catch (Exception e) {
                    handleRowError(rowIdx, info.getName(), e);
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number in settings.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
        postActionUpdate(successCount);
    }

    private int[] getSelectedRowsWithWarning() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one Thread Group from the table.", "No Rows Selected", JOptionPane.WARNING_MESSAGE);
        }
        return selectedRows;
    }

    private void updateInfoFromTable(ThreadGroupInfo info, int rowIdx) {
        info.setTargetRph(getIntegerFromTable(rowIdx, 1));
        info.setActualRph(getIntegerFromTable(rowIdx, 2));
        info.setIterationDurationSec(getDoubleFromTable(rowIdx, 3));
        info.setRampUpSec(getIntegerFromTable(rowIdx, 4));
        info.setHoldSec(getIntegerFromTable(rowIdx, 5));
        info.setRampDownSec(getIntegerFromTable(rowIdx, 6));
        info.setHttpSamplersCount(getIntegerFromTable(rowIdx, 7));
        info.setCalculatedThreads(getIntegerFromTable(rowIdx, 8));
    }

    private int getIntegerFromTable(int row, int col) {
        Object val = tableModel.getValueAt(row, col);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getDoubleFromTable(int row, int col) {
        Object val = tableModel.getValueAt(row, col);
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void updateTableFromInfo(ThreadGroupInfo info, int rowIdx) {
        tableModel.setValueAt(info.getTargetRph(), rowIdx, 1);
        tableModel.setValueAt(info.getActualRph(), rowIdx, 2);
        tableModel.setValueAt(info.getIterationDurationSec(), rowIdx, 3);
        tableModel.setValueAt(info.getRampUpSec(), rowIdx, 4);
        tableModel.setValueAt(info.getHoldSec(), rowIdx, 5);
        tableModel.setValueAt(info.getRampDownSec(), rowIdx, 6);
        tableModel.setValueAt(info.getHttpSamplersCount(), rowIdx, 7);
        tableModel.setValueAt(info.getCalculatedThreads(), rowIdx, 8);
        tableModel.setValueAt(String.format("%.0f", info.getCalculatedIntervalMs()), rowIdx, 9);
    }

    private void handleRowError(int rowIdx, String name, Exception e) {
        logArea.append("ERROR processing '" + name + "' (row " + rowIdx + "): " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
        log.error("Error processing row", e);
    }

    private void postActionUpdate(int successCount) {
        if (successCount > 0) {
            guiPackage.setDirty(true);
            guiPackage.getMainFrame().repaint();
            guiPackage.updateCurrentGui();
            logArea.append("\nApplied changes to " + successCount + " Thread Group(s). Don't forget to save the test plan.\n");
        }
    }

    private void saveSettings() {
        JMeterUtils.setProperty(PROP_STABILITY_LOAD, loadPercentageField.getText());
        JMeterUtils.setProperty(PROP_STABILITY_DUR, durationField.getText());
        JMeterUtils.setProperty(PROP_STEPUP_STEPS, numStepsField.getText());
        JMeterUtils.setProperty(PROP_STEPUP_DUR, stepDurationField.getText());
        JMeterUtils.setProperty(PROP_STEPUP_INIT, initialLoadField.getText());
        JMeterUtils.setProperty(PROP_STEPUP_INC, incrementField.getText());
        JMeterUtils.setProperty(PROP_STEPUP_RAMP, stepRampUpField.getText());
    }

    private void loadSettings() {
        loadPercentageField.setText(JMeterUtils.getPropDefault(PROP_STABILITY_LOAD, "150"));
        durationField.setText(JMeterUtils.getPropDefault(PROP_STABILITY_DUR, "3600"));
        numStepsField.setText(JMeterUtils.getPropDefault(PROP_STEPUP_STEPS, "5"));
        stepDurationField.setText(JMeterUtils.getPropDefault(PROP_STEPUP_DUR, "600"));
        initialLoadField.setText(JMeterUtils.getPropDefault(PROP_STEPUP_INIT, "50"));
        incrementField.setText(JMeterUtils.getPropDefault(PROP_STEPUP_INC, "25"));
        stepRampUpField.setText(JMeterUtils.getPropDefault(PROP_STEPUP_RAMP, "30"));
    }
}
