package com.example.osrstts.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring safety limits and usage controls.
 */
public class SafetyPanel extends JPanel {
    private final JTextField characterBudget = new JTextField("50000", 10);
    private final JComboBox<String> budgetAction = new JComboBox<>(new String[]{"Warn", "Pause", "Ignore"});
    private final JTextField chatThreshold = new JTextField("10", 8);
    private final JCheckBox autoPauseInBusyChat = new JCheckBox("Auto-pause in busy chat", true);
    private final JCheckBox enableHotkey = new JCheckBox("Enable global TTS toggle hotkey", true);
    private final JTextField hotkeyField = new JTextField("F12", 8);
    
    public SafetyPanel() {
        setupUI();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Character budget section
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3;
        JLabel budgetSection = new JLabel("Character Budget");
        budgetSection.setFont(budgetSection.getFont().deriveFont(Font.BOLD));
        add(budgetSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 1;
        add(new JLabel("Monthly character limit:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(characterBudget, c);
        
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        add(new JLabel("When limit reached:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(budgetAction, c);
        
        // Chat management section
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3; c.fill = GridBagConstraints.NONE;
        JLabel chatSection = new JLabel("Chat Management");
        chatSection.setFont(chatSection.getFont().deriveFont(Font.BOLD));
        add(chatSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        add(autoPauseInBusyChat, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 5;
        add(new JLabel("Messages/second threshold:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(chatThreshold, c);
        
        // Hotkey section
        c.gridx = 0; c.gridy = 6; c.gridwidth = 3; c.fill = GridBagConstraints.NONE;
        JLabel hotkeySection = new JLabel("Global Controls");
        hotkeySection.setFont(hotkeySection.getFont().deriveFont(Font.BOLD));
        add(hotkeySection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 7; c.gridwidth = 2;
        add(enableHotkey, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 8;
        add(new JLabel("Toggle hotkey:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(hotkeyField, c);
        
        c.gridx = 2; c.fill = GridBagConstraints.NONE;
        JButton setHotkeyBtn = new JButton("Set");
        setHotkeyBtn.addActionListener(e -> setHotkey());
        add(setHotkeyBtn, c);
        
        // Current usage display
        c.gridx = 0; c.gridy = 9; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        JPanel usagePanel = new JPanel(new BorderLayout());
        usagePanel.setBorder(BorderFactory.createTitledBorder("Current Usage"));
        usagePanel.setOpaque(false);
        
        JLabel usageStats = new JLabel("<html>" +
            "This month: 1,234 characters<br>" +
            "Today: 89 characters<br>" +
            "Current session: 23 characters" +
            "</html>");
        usagePanel.add(usageStats, BorderLayout.CENTER);
        
        JButton resetUsageBtn = new JButton("Reset");
        resetUsageBtn.addActionListener(e -> resetUsage());
        usagePanel.add(resetUsageBtn, BorderLayout.EAST);
        
        add(usagePanel, c);
        
        // Help text
        c.gridy = 10; c.anchor = GridBagConstraints.NORTHWEST;
        JTextArea helpText = new JTextArea(
            "Safety limits help prevent unexpected costs and spam:\n" +
            "• Character budget: Soft limit for monthly TTS usage\n" +
            "• Busy chat pause: Automatically pause TTS during rapid chat\n" +
            "• Global hotkey: Quickly toggle TTS on/off during gameplay"
        );
        helpText.setOpaque(false);
        helpText.setEditable(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.ITALIC, 11f));
        helpText.setRows(4);
        add(helpText, c);
    }
    
    private void setHotkey() {
        // TODO: Implement hotkey capture
        JOptionPane.showMessageDialog(this, 
            "Hotkey setting not yet implemented.\n" +
            "Current hotkey: " + hotkeyField.getText(),
            "Set Hotkey", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void resetUsage() {
        int result = JOptionPane.showConfirmDialog(this,
            "Reset usage statistics for current month?",
            "Reset Usage",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            JOptionPane.showMessageDialog(this, 
                "Usage statistics reset.\n(Implementation pending)", 
                "Usage Reset", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    // Getters for configuration values
    public int getCharacterBudget() {
        try {
            return Integer.parseInt(characterBudget.getText());
        } catch (NumberFormatException e) {
            return 50000; // Default
        }
    }
    
    public String getBudgetAction() {
        return (String) budgetAction.getSelectedItem();
    }
    
    public int getChatThreshold() {
        try {
            return Integer.parseInt(chatThreshold.getText());
        } catch (NumberFormatException e) {
            return 10; // Default
        }
    }
    
    public boolean isAutoPauseEnabled() {
        return autoPauseInBusyChat.isSelected();
    }
    
    public boolean isHotkeyEnabled() {
        return enableHotkey.isSelected();
    }
    
    public String getHotkey() {
        return hotkeyField.getText();
    }
}