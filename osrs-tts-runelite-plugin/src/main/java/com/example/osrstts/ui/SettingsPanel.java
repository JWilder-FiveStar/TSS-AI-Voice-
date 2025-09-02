package com.example.osrstts.ui;

import com.example.osrstts.usage.UsageTracker;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Basic TTS settings panel with essential controls and an "Advanced..." button.
 * Designed for simple, quick setup by most users.
 */
public class SettingsPanel extends JPanel {
    private final JCheckBox enableTts = new JCheckBox("Enable TTS");
    private final JComboBox<String> primaryProvider = 
        new JComboBox<>(new String[]{"ElevenLabs", "Azure", "Polly"});
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JButton testBtn = new JButton("Test voice...");
    private final JButton npcOverrideBtn = new JButton("Set voice for this NPC...");
    private final JProgressBar usageBar = new JProgressBar(0, 100);
    private final JLabel usageLabel = new JLabel("0 / 30k chars");
    private final JButton advancedBtn = new JButton("Advanced...");
    
    // Callbacks for parent component
    private final Consumer<Boolean> onEnableChange;
    private final Consumer<String> onProviderChange;
    private final Consumer<Integer> onVolumeChange;
    private final Runnable onTest;
    private final Runnable onNpcOverride;
    private final Runnable onOpenAdvanced;
    
    public SettingsPanel(
        boolean enabled,
        String provider,
        int vol,
        UsageTracker usage,
        Runnable onTest,
        Runnable onNpcOverride,
        Consumer<Boolean> onEnableChange,
        Consumer<String> onProviderChange,
        Consumer<Integer> onVolumeChange,
        Runnable onOpenAdvanced
    ) {
        this.onEnableChange = onEnableChange;
        this.onProviderChange = onProviderChange;
        this.onVolumeChange = onVolumeChange;
        this.onTest = onTest;
        this.onNpcOverride = onNpcOverride;
        this.onOpenAdvanced = onOpenAdvanced;
        
        setupUI(enabled, provider, vol, usage);
        setupEventHandlers();
    }
    
    private void setupUI(boolean enabled, String provider, int vol, UsageTracker usage) {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        // Initialize component states
        enableTts.setSelected(enabled);
        primaryProvider.setSelectedItem(provider);
        volume.setValue(vol);
        volume.setMajorTickSpacing(25);
        volume.setMinorTickSpacing(5);
        volume.setPaintTicks(true);
        volume.setPaintLabels(true);
        
        usageBar.setStringPainted(true);
        usageBar.setString("0%");
        
        // Layout components
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Enable TTS checkbox
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        add(enableTts, c);
        
        // Primary provider
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1;
        add(new JLabel("Primary provider:"), c);
        c.gridx = 1;
        add(primaryProvider, c);
        
        // Volume
        c.gridx = 0; c.gridy = 2;
        add(new JLabel("Master volume:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(volume, c);
        
        // Test and override buttons
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE;
        add(testBtn, c);
        c.gridx = 1;
        add(npcOverrideBtn, c);
        
        // Usage bar
        c.gridx = 0; c.gridy = 4;
        add(new JLabel("Usage (month):"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JPanel usagePanel = new JPanel(new BorderLayout(5, 0));
        usagePanel.setOpaque(false);
        usagePanel.add(usageBar, BorderLayout.CENTER);
        usagePanel.add(usageLabel, BorderLayout.EAST);
        add(usagePanel, c);
        
        // Advanced button
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        add(advancedBtn, c);
        
        // Initial usage update
        updateUsage(usage);
    }
    
    private void setupEventHandlers() {
        enableTts.addActionListener(e -> onEnableChange.accept(enableTts.isSelected()));
        primaryProvider.addActionListener(e -> onProviderChange.accept((String) primaryProvider.getSelectedItem()));
        volume.addChangeListener(e -> onVolumeChange.accept(volume.getValue()));
        testBtn.addActionListener(e -> onTest.run());
        npcOverrideBtn.addActionListener(e -> onNpcOverride.run());
        advancedBtn.addActionListener(e -> onOpenAdvanced.run());
    }
    
    /**
     * Update the usage display with current data.
     */
    public void updateUsage(UsageTracker usage) {
        if (usage == null) return;
        
        UsageTracker.UsageEstimate estimate = usage.estimateForElevenLabs();
        int percent = Math.min(100, (int) Math.round(estimate.usagePercent()));
        
        usageBar.setValue(percent);
        usageBar.setString(percent + "%");
        usageLabel.setText(estimate.currentUsage() + " / " + estimate.suggestedTierLimit() + " chars");
        
        // Color coding for usage levels
        if (percent >= 90) {
            usageBar.setForeground(Color.RED);
        } else if (percent >= 75) {
            usageBar.setForeground(Color.ORANGE);
        } else {
            usageBar.setForeground(Color.GREEN);
        }
    }
    
    /**
     * Update the enabled state of components based on TTS enabled status.
     */
    public void setTtsEnabled(boolean enabled) {
        enableTts.setSelected(enabled);
        primaryProvider.setEnabled(enabled);
        volume.setEnabled(enabled);
        testBtn.setEnabled(enabled);
        npcOverrideBtn.setEnabled(enabled);
        advancedBtn.setEnabled(enabled);
    }
    
    /**
     * Update the selected provider.
     */
    public void setSelectedProvider(String provider) {
        primaryProvider.setSelectedItem(provider);
    }
    
    /**
     * Update the volume value.
     */
    public void setVolumeValue(int volume) {
        this.volume.setValue(volume);
    }
}