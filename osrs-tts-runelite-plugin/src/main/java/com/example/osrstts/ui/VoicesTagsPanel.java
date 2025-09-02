package com.example.osrstts.ui;

import com.example.osrstts.voice.VoiceSelectionPipeline;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for configuring voices, tags, and gender settings.
 */
public class VoicesTagsPanel extends JPanel {
    private final JComboBox<VoiceSelectionPipeline.GenderStrictness> genderStrictness =
        new JComboBox<>(VoiceSelectionPipeline.GenderStrictness.values());
    private final JComboBox<String> defaultMaleVoice = new JComboBox<>();
    private final JComboBox<String> defaultFemaleVoice = new JComboBox<>();
    private final JComboBox<String> defaultKidVoice = new JComboBox<>();
    
    public VoicesTagsPanel() {
        setupUI();
        loadVoiceLists();
    }
    
    private void setupUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        
        // Gender guardrail setting
        c.gridx = 0; c.gridy = 0;
        add(new JLabel("Gender guardrail:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(genderStrictness, c);
        genderStrictness.setSelectedItem(VoiceSelectionPipeline.GenderStrictness.PREFER);
        
        // Default voice selections
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Default male voice:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(defaultMaleVoice, c);
        
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Default female voice:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(defaultFemaleVoice, c);
        
        c.gridx = 0; c.gridy = 3; c.fill = GridBagConstraints.NONE;
        add(new JLabel("Default kid voice:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        add(defaultKidVoice, c);
        
        // Tag mapping editor button
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        JButton editTagsBtn = new JButton("Edit tag → voice mappings...");
        editTagsBtn.addActionListener(e -> openTagMappingEditor());
        add(editTagsBtn, c);
        
        // Help text
        c.gridy = 5; c.anchor = GridBagConstraints.NORTHWEST;
        JTextArea helpText = new JTextArea(
            "Gender guardrail controls voice selection:\n" +
            "• STRICT: Never allow gender mismatches\n" +
            "• PREFER: Prefer gender matches, allow mismatches if needed\n" +
            "• OFF: Ignore gender completely"
        );
        helpText.setOpaque(false);
        helpText.setEditable(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.ITALIC, 11f));
        add(helpText, c);
    }
    
    private void loadVoiceLists() {
        // TODO: Load voice lists from the current provider
        // For now, add some placeholder values
        String[] placeholderVoices = {"Loading voices...", "Adam", "Rachel", "Bella"};
        
        defaultMaleVoice.setModel(new DefaultComboBoxModel<>(placeholderVoices));
        defaultFemaleVoice.setModel(new DefaultComboBoxModel<>(placeholderVoices));
        defaultKidVoice.setModel(new DefaultComboBoxModel<>(placeholderVoices));
    }
    
    private void openTagMappingEditor() {
        // TODO: Open tag mapping editor dialog
        JOptionPane.showMessageDialog(this, 
            "Tag mapping editor not yet implemented.\n" +
            "This will allow editing voice assignments for tags like:\n" +
            "elf → Elli, dwarf → Arnold, goblin → Sam, etc.",
            "Tag Mappings", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    public VoiceSelectionPipeline.GenderStrictness getGenderStrictness() {
        return (VoiceSelectionPipeline.GenderStrictness) genderStrictness.getSelectedItem();
    }
    
    public void setGenderStrictness(VoiceSelectionPipeline.GenderStrictness strictness) {
        genderStrictness.setSelectedItem(strictness);
    }
}