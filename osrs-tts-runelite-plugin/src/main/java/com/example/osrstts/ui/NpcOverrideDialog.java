package com.example.osrstts.ui;

import com.example.osrstts.tts.ElevenVoice;
import com.example.osrstts.voice.VoiceSelectionPipeline;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for setting or changing the voice for a specific NPC.
 */
public class NpcOverrideDialog extends JDialog {
    private final String npcName;
    private final Integer npcId;
    private final JComboBox<String> providerCombo;
    private final JComboBox<String> voiceCombo;
    private final JTextField searchField;
    private final JButton testButton;
    private final JButton saveButton;
    private final JButton clearButton;
    private final JButton cancelButton;
    
    private final Consumer<VoiceOverride> onSave;
    private final Runnable onClear;
    
    public NpcOverrideDialog(Window parent, String npcName, Integer npcId, 
                           Consumer<VoiceOverride> onSave, Runnable onClear) {
        super(parent, "Set Voice for " + npcName, ModalityType.APPLICATION_MODAL);
        this.npcName = npcName;
        this.npcId = npcId;
        this.onSave = onSave;
        this.onClear = onClear;
        
        // Initialize components
        this.providerCombo = new JComboBox<>(new String[]{"ElevenLabs", "Azure", "Polly"});
        this.voiceCombo = new JComboBox<>();
        this.searchField = new JTextField(20);
        this.testButton = new JButton("Test Voice");
        this.saveButton = new JButton("Save");
        this.clearButton = new JButton("Clear Assignment");
        this.cancelButton = new JButton("Cancel");
        
        setupUI();
        setupEventHandlers();
        loadVoicesForProvider();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        setResizable(false);
        
        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        
        // NPC info
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        JLabel npcLabel = new JLabel("NPC: " + npcName + (npcId != null ? " (ID: " + npcId + ")" : ""));
        npcLabel.setFont(npcLabel.getFont().deriveFont(Font.BOLD, 14f));
        mainPanel.add(npcLabel, c);
        
        // Provider selection
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1;
        mainPanel.add(new JLabel("Provider:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(providerCombo, c);
        
        // Voice search
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Search voices:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(searchField, c);
        
        // Voice selection
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("Voice:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(voiceCombo, c);
        
        // Test button
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(testButton, c);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Set default button
        getRootPane().setDefaultButton(saveButton);
        
        pack();
        setLocationRelativeTo(getParent());
    }
    
    private void setupEventHandlers() {
        providerCombo.addActionListener(e -> loadVoicesForProvider());
        
        searchField.addCaretListener(e -> filterVoices());
        
        testButton.addActionListener(e -> testSelectedVoice());
        
        saveButton.addActionListener(e -> saveVoiceOverride());
        
        clearButton.addActionListener(e -> clearVoiceAssignment());
        
        cancelButton.addActionListener(e -> dispose());
        
        // Enable/disable test and save buttons based on selection
        voiceCombo.addActionListener(e -> updateButtonStates());
    }
    
    private void loadVoicesForProvider() {
        String provider = (String) providerCombo.getSelectedItem();
        voiceCombo.removeAllItems();
        
        if ("ElevenLabs".equals(provider)) {
            loadElevenLabsVoices();
        } else if ("Azure".equals(provider)) {
            loadAzureVoices();
        } else if ("Polly".equals(provider)) {
            loadPollyVoices();
        }
        
        updateButtonStates();
    }
    
    private void loadElevenLabsVoices() {
        // TODO: Load from ElevenLabs client
        voiceCombo.addItem("Loading voices...");
        
        // Placeholder voices for now
        SwingUtilities.invokeLater(() -> {
            voiceCombo.removeAllItems();
            voiceCombo.addItem("Rachel (21m00Tcm4TlvDq8ikWAM)");
            voiceCombo.addItem("Adam (pNInz6obpgDQGcFmaJgB)");
            voiceCombo.addItem("Bella (EXAVITQu4vr4xnSDxMaL)");
            voiceCombo.addItem("Antoni (ErXwobaYiN019PkySvjV)");
            voiceCombo.addItem("Elli (MF3mGyEYCl7XYWbV9V6O)");
            voiceCombo.addItem("Josh (TxGEqnHWrfWFTfGW9XjX)");
            voiceCombo.addItem("Arnold (VR6AewLTigWG4xSOukaG)");
            voiceCombo.addItem("Sam (yoZ06aMxZJJ28mfd3POQ)");
            voiceCombo.addItem("Dorothy (ThT5KcBeYPX3keUQqHPh)");
            updateButtonStates();
        });
    }
    
    private void loadAzureVoices() {
        voiceCombo.addItem("en-US-JennyNeural");
        voiceCombo.addItem("en-US-GuyNeural");
        voiceCombo.addItem("en-US-AriaNeural");
        voiceCombo.addItem("en-US-DavisNeural");
        voiceCombo.addItem("en-GB-SoniaNeural");
        voiceCombo.addItem("en-GB-RyanNeural");
        voiceCombo.addItem("en-AU-NatashaNeural");
        voiceCombo.addItem("en-AU-WilliamNeural");
    }
    
    private void loadPollyVoices() {
        voiceCombo.addItem("Joanna");
        voiceCombo.addItem("Matthew");
        voiceCombo.addItem("Ivy");
        voiceCombo.addItem("Justin");
        voiceCombo.addItem("Kendra");
        voiceCombo.addItem("Kimberly");
        voiceCombo.addItem("Salli");
        voiceCombo.addItem("Joey");
    }
    
    private void filterVoices() {
        String searchText = searchField.getText().toLowerCase().trim();
        if (searchText.isEmpty()) {
            loadVoicesForProvider(); // Reset to full list
            return;
        }
        
        // TODO: Implement voice filtering based on search text
        // For now, just show a message
        if (searchText.length() >= 2) {
            // This would filter the voice list in a real implementation
        }
    }
    
    private void testSelectedVoice() {
        String selectedVoice = (String) voiceCombo.getSelectedItem();
        if (selectedVoice == null) return;
        
        // TODO: Implement voice testing
        JOptionPane.showMessageDialog(this,
            "Testing voice: " + selectedVoice + "\n" +
            "Playing sample: \"Hello, this is a test of the " + selectedVoice + " voice.\"\n\n" +
            "(Voice testing not yet implemented)",
            "Test Voice",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void saveVoiceOverride() {
        String provider = (String) providerCombo.getSelectedItem();
        String voice = (String) voiceCombo.getSelectedItem();
        
        if (provider == null || voice == null) {
            JOptionPane.showMessageDialog(this,
                "Please select both a provider and a voice.",
                "Invalid Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Extract voice ID and label
        String voiceId, voiceLabel;
        if ("ElevenLabs".equals(provider)) {
            voiceId = ElevenVoice.extractVoiceId(voice);
            voiceLabel = voice.contains("(") ? voice.substring(0, voice.indexOf("(")).trim() : voice;
        } else {
            voiceId = voice;
            voiceLabel = voice;
        }
        
        VoiceOverride override = new VoiceOverride(provider, voiceId, voiceLabel);
        onSave.accept(override);
        dispose();
    }
    
    private void clearVoiceAssignment() {
        int result = JOptionPane.showConfirmDialog(this,
            "Clear voice assignment for " + npcName + "?\n" +
            "The NPC will be automatically assigned a voice next time they speak.",
            "Clear Voice Assignment",
            JOptionPane.YES_NO_OPTION);
            
        if (result == JOptionPane.YES_OPTION) {
            onClear.run();
            dispose();
        }
    }
    
    private void updateButtonStates() {
        boolean hasSelection = voiceCombo.getSelectedItem() != null && 
                              !voiceCombo.getSelectedItem().toString().contains("Loading");
        testButton.setEnabled(hasSelection);
        saveButton.setEnabled(hasSelection);
    }
    
    /**
     * Data class for voice override information.
     */
    public static record VoiceOverride(
        String provider,
        String voiceId,
        String voiceLabel
    ) {}
}