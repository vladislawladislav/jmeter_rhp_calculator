package com.jmeter.plugins.rphcalculator;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.plugin.MenuCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RPHCalculatorMenuPlugin implements MenuCreator {

    private static final Logger log = LoggerFactory.getLogger(RPHCalculatorMenuPlugin.class);

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            JMenuItem menuItem = new JMenuItem("RPH Calculator for Ultimate Thread Groups");
            menuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    GuiPackage guiPackage = GuiPackage.getInstance();
                    if (guiPackage != null) {
                        RPHCalculatorFrame frame = new RPHCalculatorFrame(guiPackage);
                        frame.setVisible(true);
                    }
                }
            });
            return new JMenuItem[]{menuItem};
        }
        return new JMenuItem[0];
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
    }
}