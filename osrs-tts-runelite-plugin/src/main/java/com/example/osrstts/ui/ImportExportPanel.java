package com.example.osrstts.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for importing and exporting TTS configuration and voice assignments.
 */
public class ImportExportPanel extends JPanel {
    
    public ImportExportPanel() {
        setupUI();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Voice mappings section
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        JLabel voiceMappingsSection = new JLabel("Voice Mappings");
        voiceMappingsSection.setFont(voiceMappingsSection.getFont().deriveFont(Font.BOLD));
        add(voiceMappingsSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.HORIZONTAL;
        JButton exportMappingsBtn = new JButton("Export osrs-voices.json");
        exportMappingsBtn.addActionListener(e -> exportVoiceMappings());
        add(exportMappingsBtn, c);
        
        c.gridx = 1;
        JButton importMappingsBtn = new JButton("Import osrs-voices.json");
        importMappingsBtn.addActionListener(e -> importVoiceMappings());
        add(importMappingsBtn, c);
        
        // Voice assignments section
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        JLabel assignmentsSection = new JLabel("Voice Assignments");
        assignmentsSection.setFont(assignmentsSection.getFont().deriveFont(Font.BOLD));
        add(assignmentsSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.HORIZONTAL;
        JButton exportAssignmentsBtn = new JButton("Export voice assignments");
        exportAssignmentsBtn.addActionListener(e -> exportVoiceAssignments());
        add(exportAssignmentsBtn, c);
        
        c.gridx = 1;
        JButton importAssignmentsBtn = new JButton("Import voice assignments");
        importAssignmentsBtn.addActionListener(e -> importVoiceAssignments());
        add(importAssignmentsBtn, c);
        
        // Full configuration section
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        JLabel configSection = new JLabel("Full Configuration");
        configSection.setFont(configSection.getFont().deriveFont(Font.BOLD));
        add(configSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 5; c.fill = GridBagConstraints.HORIZONTAL;
        JButton exportAllBtn = new JButton("Export all settings");
        exportAllBtn.addActionListener(e -> exportAllSettings());
        add(exportAllBtn, c);
        
        c.gridx = 1;
        JButton importAllBtn = new JButton("Import all settings");
        importAllBtn.addActionListener(e -> importAllSettings());
        add(importAllBtn, c);
        
        // Reset section
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        JLabel resetSection = new JLabel("Reset");
        resetSection.setFont(resetSection.getFont().deriveFont(Font.BOLD));
        add(resetSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 7; c.fill = GridBagConstraints.HORIZONTAL;
        JButton resetMappingsBtn = new JButton("Reset to defaults");
        resetMappingsBtn.addActionListener(e -> resetToDefaults());
        add(resetMappingsBtn, c);
        
        c.gridx = 1;
        JButton clearAssignmentsBtn = new JButton("Clear all assignments");
        clearAssignmentsBtn.addActionListener(e -> clearAllAssignments());
        add(clearAssignmentsBtn, c);
        
        // Help text
        c.gridx = 0; c.gridy = 8; c.gridwidth = 2; c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH; c.weightx = 1.0; c.weighty = 1.0;
        JTextArea helpText = new JTextArea(
            "Import/Export allows you to:\n\n" +
            "• Share voice configurations with other users\n" +
            "• Backup your voice assignments and mappings\n" +
            "• Restore settings after plugin updates\n\n" +
            "Voice mappings: Rules for which voices to use for NPCs/tags\n" +
            "Voice assignments: Persistent assignments for specific NPCs\n\n" +
            "Files are saved in JSON format in the plugin config directory."
        );
        helpText.setOpaque(false);
        helpText.setEditable(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.ITALIC, 11f));
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        add(helpText, c);
    }
    
    private void exportVoiceMappings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Voice Mappings");
        chooser.setSelectedFile(new java.io.File("osrs-voices.json"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement export
            JOptionPane.showMessageDialog(this, 
                "Voice mappings exported to:\n" + chooser.getSelectedFile().getAbsolutePath() +
                "\n\n(Implementation pending)", 
                "Export Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void importVoiceMappings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Voice Mappings");
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement import
            JOptionPane.showMessageDialog(this, 
                "Voice mappings imported from:\n" + chooser.getSelectedFile().getAbsolutePath() +
                "\n\n(Implementation pending)", 
                "Import Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void exportVoiceAssignments() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Voice Assignments");
        chooser.setSelectedFile(new java.io.File("voice-assignments.json"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement export
            JOptionPane.showMessageDialog(this, 
                "Voice assignments exported to:\n" + chooser.getSelectedFile().getAbsolutePath() +
                "\n\n(Implementation pending)", 
                "Export Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void importVoiceAssignments() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Voice Assignments");
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement import
            JOptionPane.showMessageDialog(this, 
                "Voice assignments imported from:\n" + chooser.getSelectedFile().getAbsolutePath() +
                "\n\n(Implementation pending)", 
                "Import Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void exportAllSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export All TTS Settings");
        chooser.setSelectedFile(new java.io.File("osrs-tts-settings.json"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            // TODO: Implement export
            JOptionPane.showMessageDialog(this, 
                "All settings exported to:\n" + chooser.getSelectedFile().getAbsolutePath() +
                "\n\n(Implementation pending)", 
                "Export Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void importAllSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import All TTS Settings");
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            int result = JOptionPane.showConfirmDialog(this,
                "This will overwrite all current TTS settings.\n" +
                "Are you sure you want to continue?",
                "Import All Settings",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (result == JOptionPane.YES_OPTION) {
                // TODO: Implement import
                JOptionPane.showMessageDialog(this, 
                    "All settings imported from:\n" + chooser.getSelectedFile().getAbsolutePath() +
                    "\n\n(Implementation pending)", 
                    "Import Complete", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
    
    private void resetToDefaults() {
        int result = JOptionPane.showConfirmDialog(this,
            "Reset all voice mappings to default values?\n" +
            "This will not affect your persistent voice assignments.",
            "Reset to Defaults",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            // TODO: Implement reset
            JOptionPane.showMessageDialog(this, 
                "Voice mappings reset to defaults.\n(Implementation pending)", 
                "Reset Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void clearAllAssignments() {
        int result = JOptionPane.showConfirmDialog(this,
            "Clear all persistent voice assignments?\n" +
            "NPCs will be re-assigned voices automatically next time they speak.",
            "Clear All Assignments",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            // TODO: Implement clear
            JOptionPane.showMessageDialog(this, 
                "All voice assignments cleared.\n(Implementation pending)", 
                "Clear Complete", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }
}