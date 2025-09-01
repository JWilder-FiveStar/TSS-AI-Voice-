package com.example.osrstts;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;

import com.example.osrstts.tts.AzureSpeechTtsClient;
import com.example.osrstts.tts.PollyTtsClient;
import com.example.osrstts.tts.TtsClient;
import com.example.osrstts.voice.VoiceSelection;

public class OsrsTtsPanel extends JPanel {
    private JComboBox<String> providerSelector;
    private JTextField azureKeyField;
    private JTextField azureRegionField;
    private JButton saveButton;
    private JButton testVoiceButton;
    private JTextArea outputArea;
    private final OsrsTtsConfig cfg = new OsrsTtsConfig();

    public OsrsTtsPanel() {
        setLayout(new BorderLayout());

        // Create components
    providerSelector = new JComboBox<>(new String[]{"Azure", "Polly"});
    providerSelector.setSelectedItem(cfg.getProvider());
    azureKeyField = new JTextField(cfg.getAzureKey(), 36);
    azureRegionField = new JTextField(cfg.getAzureRegion(), 12);
    saveButton = new JButton("Save");
    testVoiceButton = new JButton("Test Voice");
        outputArea = new JTextArea(10, 30);
        outputArea.setEditable(false);

        // Add action listener for the test voice button
    testVoiceButton.addActionListener(e -> testVoice());
    saveButton.addActionListener(e -> save());

        // Create a panel for the TTS API selector and button
    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4,4,4,4);
    c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_END;
    controlPanel.add(new JLabel("Provider:"), c);
    c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
    controlPanel.add(providerSelector, c);

    c.gridx = 0; c.gridy = 1; c.anchor = GridBagConstraints.LINE_END;
    controlPanel.add(new JLabel("Azure Key:"), c);
    c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
    controlPanel.add(azureKeyField, c);

    c.gridx = 0; c.gridy = 2; c.anchor = GridBagConstraints.LINE_END;
    controlPanel.add(new JLabel("Azure Region:"), c);
    c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
    controlPanel.add(azureRegionField, c);

    c.gridx = 0; c.gridy = 3; c.anchor = GridBagConstraints.LINE_END;
    controlPanel.add(saveButton, c);
    c.gridx = 1; c.anchor = GridBagConstraints.LINE_START;
    controlPanel.add(testVoiceButton, c);

        // Add components to the main panel
        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);
    }

    private void testVoice() {
        String provider = (String) providerSelector.getSelectedItem();
        outputArea.append("Testing voice with " + provider + "...\n");
        // Save first to ensure config reflects UI
        save();

        new Thread(() -> {
            try {
                String text = "Hello from OSRS TTS";
                TtsClient client;
                VoiceSelection sel;
                if ("Azure".equalsIgnoreCase(provider)) {
                    String key = cfg.getAzureKey();
                    String region = cfg.getAzureRegion();
                    if (key == null || key.isBlank()) {
                        appendSafe("Azure key is missing.\n");
                        return;
                    }
                    client = new AzureSpeechTtsClient(key, region, cfg.getAudioOutputFormat());
                    sel = VoiceSelection.of(cfg.getNarratorVoice(), cfg.getNarratorStyle());
                } else {
                    client = new PollyTtsClient();
                    sel = VoiceSelection.of("Joanna", null);
                }
                byte[] audio = client.synthesize(text, sel);
                playWav(audio);
                appendSafe("Test voice played.\n");
            } catch (Exception ex) {
                appendSafe("Test failed: " + ex.getMessage() + "\n");
            }
        }, "tts-test-thread").start();
    }

    private void save() {
        cfg.setProvider((String) providerSelector.getSelectedItem());
        if (!azureKeyField.getText().isBlank()) {
            cfg.setAzureKey(azureKeyField.getText().trim());
        }
        if (!azureRegionField.getText().isBlank()) {
            cfg.setAzureRegion(azureRegionField.getText().trim());
        }
        outputArea.append("Settings saved.\n");
    }

    private void playWav(byte[] data) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        }
    }

    private void appendSafe(String s) {
        SwingUtilities.invokeLater(() -> outputArea.append(s));
    }
}