package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.timers.gui.AbstractTimerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import java.awt.*;

public class RPHTimerGui extends AbstractTimerGui {

    private static final long serialVersionUID = 1L;

    private JTextField targetRphField;
    private JTextField iterationDurationField;
    private JTextField intervalField;
    private JComboBox<String> modeCombo;

    public RPHTimerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createTitledBorder("RPH Timer"));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Target RPH:"), gbc);
        gbc.gridx = 1;
        targetRphField = new JTextField("100", 10);
        targetRphField.setEditable(false);
        panel.add(targetRphField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Iteration duration (sec):"), gbc);
        gbc.gridx = 1;
        iterationDurationField = new JTextField("10", 10);
        iterationDurationField.setEditable(false);
        panel.add(iterationDurationField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Interval (ms):"), gbc);
        gbc.gridx = 1;
        intervalField = new JTextField("36000", 10);
        intervalField.setEditable(false);
        panel.add(intervalField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Distribution:"), gbc);
        gbc.gridx = 1;
        modeCombo = new JComboBox<>(new String[]{RPHTimer.MODE_CONSTANT, RPHTimer.MODE_POISSON});
        modeCombo.setEnabled(false);
        panel.add(modeCombo, gbc);

        add(panel, BorderLayout.CENTER);
    }

    @Override
    public TestElement createTestElement() {
        RPHTimer timer = new RPHTimer();
        modifyTestElement(timer);
        return timer;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
        if (element instanceof RPHTimer) {
            // Values are set by the calculator, not manually edited
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof RPHTimer) {
            RPHTimer timer = (RPHTimer) element;
            targetRphField.setText(String.valueOf(timer.getTargetRph()));
            iterationDurationField.setText(String.valueOf(timer.getIterationDuration()));
            intervalField.setText(String.format("%.0f", timer.getIntervalMs()));
            modeCombo.setSelectedItem(timer.getDistributionMode());
        }
    }

    @Override
    public String getLabelResource() {
        return null;
    }

    @Override
    public String getStaticLabel() {
        return "RPH Timer";
    }
}