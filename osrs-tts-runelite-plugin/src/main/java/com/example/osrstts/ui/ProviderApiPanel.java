package com.example.osrstts.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring provider settings and API credentials.
 */
public class ProviderApiPanel extends JPanel {
    private final JTextField providerPriority = new JTextField("ElevenLabs, Azure, Polly", 20);
    private final JPasswordField elevenLabsKey = new JPasswordField(25);
    private final JTextField modelId = new JTextField("eleven_turbo_v2_5", 20);
    private final JSlider stability = new JSlider(0, 100, 60);
    private final JSlider similarityBoost = new JSlider(0, 100, 75);
    private final JSlider styleExaggeration = new JSlider(0, 100, 0);
    private final JCheckBox useV3Tags = new JCheckBox("Use v3 audio tags (emotion/accents)");
    private final JPasswordField azureKey = new JPasswordField(25);
    private final JTextField azureRegion = new JTextField("eastus", 15);
    
    public ProviderApiPanel() {
        setupUI();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Provider priority
        c.gridx = 0; c.gridy = 0;
        add(new JLabel("Provider priority:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(providerPriority, c);
        
        // ElevenLabs section
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        JLabel elevenSection = new JLabel("ElevenLabs Settings");
        elevenSection.setFont(elevenSection.getFont().deriveFont(Font.BOLD));
        add(elevenSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 2;
        add(new JLabel("API Key:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(elevenLabsKey, c);
        
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Model ID:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(modelId, c);
        
        // Sliders with labels
        c.gridx = 0; c.gridy = 4; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Stability:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        stability.setMajorTickSpacing(25);
        stability.setPaintTicks(true);
        stability.setPaintLabels(true);
        add(stability, c);
        
        c.gridx = 0; c.gridy = 5; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Similarity boost:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        similarityBoost.setMajorTickSpacing(25);
        similarityBoost.setPaintTicks(true);
        similarityBoost.setPaintLabels(true);
        add(similarityBoost, c);
        
        c.gridx = 0; c.gridy = 6; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Style exaggeration:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        styleExaggeration.setMajorTickSpacing(25);
        styleExaggeration.setPaintTicks(true);
        styleExaggeration.setPaintLabels(true);
        add(styleExaggeration, c);
        
        c.gridx = 0; c.gridy = 7; c.gridwidth = 2;
        add(useV3Tags, c);
        
        // Azure section
        c.gridx = 0; c.gridy = 8; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        JLabel azureSection = new JLabel("Azure Settings");
        azureSection.setFont(azureSection.getFont().deriveFont(Font.BOLD));
        add(azureSection, c);
        
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 9;
        add(new JLabel("API Key:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(azureKey, c);
        
        c.gridx = 0; c.gridy = 10; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Region:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(azureRegion, c);
        
        // Test buttons
        c.gridx = 0; c.gridy = 11; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        JButton testElevenLabs = new JButton("Test ElevenLabs Connection");
        testElevenLabs.addActionListener(e -> testElevenLabsConnection());
        add(testElevenLabs, c);
        
        c.gridy = 12;
        JButton testAzure = new JButton("Test Azure Connection");
        testAzure.addActionListener(e -> testAzureConnection());
        add(testAzure, c);
    }
    
    private void testElevenLabsConnection() {
        // TODO: Implement ElevenLabs connection test
        JOptionPane.showMessageDialog(this, 
            "ElevenLabs connection test not yet implemented.", 
            "Test Connection", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void testAzureConnection() {
        // TODO: Implement Azure connection test
        JOptionPane.showMessageDialog(this, 
            "Azure connection test not yet implemented.", 
            "Test Connection", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    // Getters for configuration values
    public String getElevenLabsKey() {
        return new String(elevenLabsKey.getPassword());
    }
    
    public String getModelId() {
        return modelId.getText();
    }
    
    public int getStability() {
        return stability.getValue();
    }
    
    public int getSimilarityBoost() {
        return similarityBoost.getValue();
    }
    
    public int getStyleExaggeration() {
        return styleExaggeration.getValue();
    }
    
    public boolean isUseV3Tags() {
        return useV3Tags.isSelected();
    }
}