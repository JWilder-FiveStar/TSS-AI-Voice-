package com.example.osrstts.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring cache settings and management.
 */
public class CachingPanel extends JPanel {
    private final JTextField cacheDirectory = new JTextField("./cache/osrs-tts", 25);
    private final JTextField cacheVersion = new JTextField("1", 8);
    private final JCheckBox singleFlight = new JCheckBox("Prevent duplicate synthesis (single-flight)", true);
    private final JLabel cacheStatsLabel = new JLabel("Cache: 0 files, 0 MB");
    
    public CachingPanel() {
        setupUI();
        updateCacheStats();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Cache directory
        c.gridx = 0; c.gridy = 0;
        add(new JLabel("Cache directory:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(cacheDirectory, c);
        
        c.gridx = 2; c.fill = GridBagConstraints.NONE;
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> browseCacheDirectory());
        add(browseBtn, c);
        
        // Cache version
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Cache version:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(cacheVersion, c);
        
        // Single-flight option
        c.gridx = 0; c.gridy = 2; c.gridwidth = 3;
        add(singleFlight, c);
        
        // Cache statistics
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3;
        add(cacheStatsLabel, c);
        
        // Cache management buttons
        c.gridx = 0; c.gridy = 4; c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JButton clearNpcBtn = new JButton("Clear this NPC's cache");
        clearNpcBtn.addActionListener(e -> clearNpcCache());
        add(clearNpcBtn, c);
        
        c.gridx = 1;
        JButton clearAllBtn = new JButton("Clear ALL cache");
        clearAllBtn.addActionListener(e -> clearAllCache());
        add(clearAllBtn, c);
        
        c.gridx = 2;
        JButton refreshStatsBtn = new JButton("Refresh Stats");
        refreshStatsBtn.addActionListener(e -> updateCacheStats());
        add(refreshStatsBtn, c);
        
        // Help text
        c.gridx = 0; c.gridy = 5; c.gridwidth = 3; c.anchor = GridBagConstraints.NORTHWEST;
        JTextArea helpText = new JTextArea(
            "Cache version: Increment to invalidate all cached audio when mappings change.\n" +
            "Single-flight: Prevents duplicate synthesis requests for the same audio.\n" +
            "Audio files are cached indefinitely for consistent playback."
        );
        helpText.setOpaque(false);
        helpText.setEditable(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.ITALIC, 11f));
        helpText.setRows(3);
        add(helpText, c);
    }
    
    private void browseCacheDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new java.io.File(cacheDirectory.getText()));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            cacheDirectory.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void clearNpcCache() {
        String npcName = JOptionPane.showInputDialog(this, 
            "Enter NPC name to clear cache for:", 
            "Clear NPC Cache", 
            JOptionPane.QUESTION_MESSAGE);
            
        if (npcName != null && !npcName.trim().isEmpty()) {
            // TODO: Implement NPC-specific cache clearing
            JOptionPane.showMessageDialog(this, 
                "Cleared cache for NPC: " + npcName + "\n(Implementation pending)", 
                "Cache Cleared", 
                JOptionPane.INFORMATION_MESSAGE);
            updateCacheStats();
        }
    }
    
    private void clearAllCache() {
        int result = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to clear ALL cached audio?\n" +
            "This will force re-synthesis of all previously heard dialogue.",
            "Clear All Cache",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            // TODO: Implement full cache clearing
            JOptionPane.showMessageDialog(this, 
                "All cache cleared.\n(Implementation pending)", 
                "Cache Cleared", 
                JOptionPane.INFORMATION_MESSAGE);
            updateCacheStats();
        }
    }
    
    private void updateCacheStats() {
        // TODO: Get real cache statistics
        cacheStatsLabel.setText("Cache: Loading... (Implementation pending)");
    }
    
    // Getters for configuration values
    public String getCacheDirectory() {
        return cacheDirectory.getText();
    }
    
    public String getCacheVersion() {
        return cacheVersion.getText();
    }
    
    public boolean isSingleFlightEnabled() {
        return singleFlight.isSelected();
    }
}