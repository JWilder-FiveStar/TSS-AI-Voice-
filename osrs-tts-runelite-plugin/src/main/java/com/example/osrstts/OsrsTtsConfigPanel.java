package com.example.osrstts;

import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class OsrsTtsConfigPanel extends PluginPanel {
    private final OsrsTtsPlugin plugin;
    private final OsrsTtsRlConfig config;
    
    private JTextField azureKeyField;
    private JTextField azureRegionField;
    private JTextField narratorVoiceField;
    private JCheckBox enabledCheckbox;
    private JCheckBox narratorEnabledCheckbox;
    private JComboBox<OsrsTtsRlConfig.Provider> providerCombo;

    public OsrsTtsConfigPanel(OsrsTtsPlugin plugin, OsrsTtsRlConfig config) {
        this.plugin = plugin;
        this.config = config;
        
        setLayout(new GridBagLayout());
        initComponents();
    }

    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Enable TTS checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        enabledCheckbox = new JCheckBox("Enable TTS", config.enabled());
        add(enabledCheckbox, gbc);

        // Provider selection
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        add(new JLabel("TTS Provider:"), gbc);
        gbc.gridx = 1;
        providerCombo = new JComboBox<>(OsrsTtsRlConfig.Provider.values());
        providerCombo.setSelectedItem(config.provider());
        add(providerCombo, gbc);

        // Azure section header
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel azureHeader = new JLabel("Azure Speech Settings");
        azureHeader.setFont(azureHeader.getFont().deriveFont(Font.BOLD));
        add(azureHeader, gbc);

        // Azure Key
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        add(new JLabel("Azure Speech Key:"), gbc);
        gbc.gridx = 1;
        azureKeyField = new JTextField(config.azureKey(), 20);
        add(azureKeyField, gbc);

        // Azure Region
        gbc.gridx = 0; gbc.gridy = 4;
        add(new JLabel("Azure Region:"), gbc);
        gbc.gridx = 1;
        azureRegionField = new JTextField(config.azureRegion(), 20);
        add(azureRegionField, gbc);

        // Test Voice Button
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton testButton = new JButton("🔊 Test Voice");
        testButton.setPreferredSize(new Dimension(200, 30));
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
                plugin.testVoice();
            }
        });
        add(testButton, gbc);

        // Voice Settings section header
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        JLabel voiceHeader = new JLabel("Voice Settings");
        voiceHeader.setFont(voiceHeader.getFont().deriveFont(Font.BOLD));
        add(voiceHeader, gbc);

        // Narrator enabled
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        narratorEnabledCheckbox = new JCheckBox("Narrator for books/journals", config.narratorEnabled());
        add(narratorEnabledCheckbox, gbc);

        // Narrator Voice
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 1;
        add(new JLabel("Narrator Voice:"), gbc);
        gbc.gridx = 1;
        narratorVoiceField = new JTextField(config.narratorVoice(), 20);
        add(narratorVoiceField, gbc);

        // Save Button
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JButton saveButton = new JButton("💾 Save Settings");
        saveButton.setPreferredSize(new Dimension(200, 30));
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
                JOptionPane.showMessageDialog(OsrsTtsConfigPanel.this, 
                    "Settings saved successfully!", "Settings Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        add(saveButton, gbc);
    }

    private void saveSettings() {
        // This would save to RuneLite's config system
        // For now, we'll update the plugin's internal config directly
        if (plugin.config != null) {
            plugin.config.setAzureKey(azureKeyField.getText().trim());
            plugin.config.setAzureRegion(azureRegionField.getText().trim());
            plugin.config.setNarratorVoice(narratorVoiceField.getText().trim());
            plugin.config.setNarratorEnabled(narratorEnabledCheckbox.isSelected());
            plugin.config.setProvider(((OsrsTtsRlConfig.Provider) providerCombo.getSelectedItem()).name());
        }
    }
}
