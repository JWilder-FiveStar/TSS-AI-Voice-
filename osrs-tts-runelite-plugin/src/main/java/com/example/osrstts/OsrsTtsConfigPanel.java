package com.example.osrstts;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.config.ConfigManager;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class OsrsTtsConfigPanel extends PluginPanel {
    private final OsrsTtsPlugin plugin;
    private final OsrsTtsRlConfig config;
    private final ConfigManager configManager;

    private JPasswordField azureKeyField;
    private JToggleButton showKeyToggle;
    private JComboBox<String> azureRegionCombo;
    // Voice selection now via pop-out selectors
    private JTextField narratorVoiceField;
    private JButton narratorChooseBtn;
    private JTextField playerVoiceField;
    private JButton playerChooseBtn;
    private JCheckBox enabledCheckbox;
    private JCheckBox narratorEnabledCheckbox;
    private JComboBox<OsrsTtsRlConfig.Provider> providerCombo;

    // Azure UI refs for toggling
    private JLabel azureHeaderLabel;
    private JLabel azureKeyLabel;
    private JPanel azureKeyRow;
    private JLabel azureRegionLabel;

    // ElevenLabs UI
    private JPasswordField elevenKeyField;
    private JToggleButton showElevenToggle;
    private JTextField elevenModelField;
    private JLabel elevenHeaderLabel;
    private JLabel elevenKeyLabel;
    private JPanel elevenKeyRow;
    private JLabel elevenModelLabel;

    private final List<String> availableVoices = new ArrayList<>();
    private final JLabel voicesStatusLabel = new JLabel("Voices: 0");

    private javax.swing.Timer debounceTimer;

    private static final List<String> AZURE_REGIONS = Arrays.asList(
            "eastus", "eastus2", "southcentralus", "westus", "westus2", "westus3",
            "centralus", "northcentralus",
            "canadacentral", "brazilsouth",
            "northeurope", "westeurope", "uksouth",
            "francecentral", "germanywestcentral", "switzerlandnorth", "norwayeast",
            "eastasia", "southeastasia", "japaneast", "koreacentral",
            "australiaeast", "australiasoutheast",
            "uaenorth", "southafricanorth"
    );

    public OsrsTtsConfigPanel(OsrsTtsPlugin plugin, OsrsTtsRlConfig config, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;

        setLayout(new GridBagLayout());
        initComponents();
    }

    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

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
        azureHeaderLabel = new JLabel("Azure Speech Settings");
        azureHeaderLabel.setFont(azureHeaderLabel.getFont().deriveFont(Font.BOLD));
        add(azureHeaderLabel, gbc);

        // Azure Key with show/hide toggle
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        azureKeyLabel = new JLabel("Azure Speech Key:");
        add(azureKeyLabel, gbc);
        gbc.gridx = 1;
        azureKeyRow = new JPanel(new BorderLayout(5, 0));
        azureKeyField = new JPasswordField(config.azureKey(), 20);
        azureKeyField.setEchoChar('•');
        showKeyToggle = new JToggleButton("Show");
        showKeyToggle.addActionListener(e -> {
            if (showKeyToggle.isSelected()) {
                azureKeyField.setEchoChar((char)0);
                showKeyToggle.setText("Hide");
            } else {
                azureKeyField.setEchoChar('•');
                showKeyToggle.setText("Show");
            }
        });
        azureKeyRow.add(azureKeyField, BorderLayout.CENTER);
        azureKeyRow.add(showKeyToggle, BorderLayout.EAST);
        add(azureKeyRow, gbc);

        // Azure Region (dropdown)
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        azureRegionLabel = new JLabel("Azure Region:");
        add(azureRegionLabel, gbc);
        gbc.gridx = 1;
        azureRegionCombo = new JComboBox<>(new DefaultComboBoxModel<>(AZURE_REGIONS.toArray(new String[0])));
        String preferredRegion;
        try { preferredRegion = config.azureRegionSelect().name(); } catch (Throwable t) { preferredRegion = null; }
        String currentRegion = (config.azureRegion() != null && !config.azureRegion().isBlank()) ? config.azureRegion() : preferredRegion;
        if (currentRegion != null && !currentRegion.isBlank() && !AZURE_REGIONS.contains(currentRegion)) {
            azureRegionCombo.addItem(currentRegion);
        }
        azureRegionCombo.setSelectedItem(currentRegion != null ? currentRegion : "eastus");
        add(azureRegionCombo, gbc);

        // Voice status label (right aligned)
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        voicesStatusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(voicesStatusLabel, gbc);

        // ElevenLabs section header
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        elevenHeaderLabel = new JLabel("ElevenLabs Settings");
        elevenHeaderLabel.setFont(elevenHeaderLabel.getFont().deriveFont(Font.BOLD));
        add(elevenHeaderLabel, gbc);

        // ElevenLabs API Key with show/hide
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 1;
        elevenKeyLabel = new JLabel("ElevenLabs API Key:");
        add(elevenKeyLabel, gbc);
        gbc.gridx = 1;
        elevenKeyRow = new JPanel(new BorderLayout(5, 0));
        elevenKeyField = new JPasswordField(plugin != null && plugin.config != null ? plugin.config.getElevenKey() : "", 20);
        elevenKeyField.setEchoChar('•');
        showElevenToggle = new JToggleButton("Show");
        showElevenToggle.addActionListener(e -> {
            if (showElevenToggle.isSelected()) {
                elevenKeyField.setEchoChar((char)0);
                showElevenToggle.setText("Hide");
            } else {
                elevenKeyField.setEchoChar('•');
                showElevenToggle.setText("Show");
            }
        });
        elevenKeyRow.add(elevenKeyField, BorderLayout.CENTER);
        elevenKeyRow.add(showElevenToggle, BorderLayout.EAST);
        add(elevenKeyRow, gbc);

        // ElevenLabs Model
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 1;
        elevenModelLabel = new JLabel("ElevenLabs Model:");
        add(elevenModelLabel, gbc);
        gbc.gridx = 1;
        elevenModelField = new JTextField(plugin != null && plugin.config != null ? plugin.config.getElevenModel() : "eleven_turbo_v2_5", 20);
        add(elevenModelField, gbc);

        // Test buttons
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 1;
        JButton testButton = new JButton("🔊 Test Voice");
        add(testButton, gbc);
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
                plugin.testVoice();
            }
        });
        gbc.gridx = 1; gbc.gridy = 9; gbc.gridwidth = 1;
        JButton testPlayerButton = new JButton("🔊 Test Player Voice");
        add(testPlayerButton, gbc);
        testPlayerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
                plugin.testPlayerVoice();
            }
        });

        // Voice Settings section header
        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 2;
        JLabel voiceHeader = new JLabel("Voice Settings");
        voiceHeader.setFont(voiceHeader.getFont().deriveFont(Font.BOLD));
        add(voiceHeader, gbc);

        // Narrator enabled
        gbc.gridx = 0; gbc.gridy = 11; gbc.gridwidth = 2;
        narratorEnabledCheckbox = new JCheckBox("Narrator for books/journals", config.narratorEnabled());
        add(narratorEnabledCheckbox, gbc);

        // Narrator Voice picker (pop-out)
        gbc.gridx = 0; gbc.gridy = 12; gbc.gridwidth = 1;
        add(new JLabel("Narrator Voice:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        JPanel narrRow = new JPanel(new BorderLayout(5, 0));
        narratorVoiceField = new JTextField(config.narratorVoice());
        narratorVoiceField.setEditable(false);
        narratorChooseBtn = new JButton("Choose…");
        narratorChooseBtn.addActionListener(e -> openVoiceMenu(narratorChooseBtn, true));
        narrRow.add(narratorVoiceField, BorderLayout.CENTER);
        narrRow.add(narratorChooseBtn, BorderLayout.EAST);
        add(narrRow, gbc);

        // Player Voice picker (pop-out)
        gbc.gridx = 0; gbc.gridy = 13; gbc.gridwidth = 1;
        add(new JLabel("Player Voice:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        JPanel playerRow = new JPanel(new BorderLayout(5, 0));
        String initialPlayer = plugin != null && plugin.config != null ? plugin.config.getPlayerVoice() : config.playerVoice();
        playerVoiceField = new JTextField(initialPlayer);
        playerVoiceField.setEditable(false);
        playerChooseBtn = new JButton("Choose…");
        playerChooseBtn.addActionListener(e -> openVoiceMenu(playerChooseBtn, false));
        playerRow.add(playerVoiceField, BorderLayout.CENTER);
        playerRow.add(playerChooseBtn, BorderLayout.EAST);
        add(playerRow, gbc);

        // Save Button
        gbc.gridx = 0; gbc.gridy = 14; gbc.gridwidth = 1;
        JButton saveButton = new JButton("💾 Save Settings");
        add(saveButton, gbc);
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
                JOptionPane.showMessageDialog(OsrsTtsConfigPanel.this,
                        "Settings saved successfully!", "Settings Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Lore Voice Selector
        gbc.gridx = 1; gbc.gridy = 14; gbc.gridwidth = 1;
        JButton loreSelectorBtn = new JButton("📜 Lore Voice Selector…");
        add(loreSelectorBtn, gbc);
        loreSelectorBtn.addActionListener(e -> openLoreSelectorDialog());

        // Wire provider change to update UI and save immediately
        providerCombo.addActionListener(e -> onProviderChanged());
        // React to key/region/model changes to auto-load voices (debounced)
        azureKeyField.getDocument().addDocumentListener(reloadOnChange());
        elevenKeyField.getDocument().addDocumentListener(reloadOnChange());
        elevenModelField.getDocument().addDocumentListener(reloadOnChange());
        azureRegionCombo.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) scheduleAutoLoad(); });

        // Apply initial provider UI and trigger initial load
        onProviderChanged();
        scheduleAutoLoad();
    }

    private void openVoiceMenu(JButton anchor, boolean narrator) {
        // Ensure we have voices; if not, try auto-load
        if (availableVoices.isEmpty()) {
            autoLoadVoicesForProvider();
            if (availableVoices.isEmpty()) {
                String hint = voicesStatusLabel.getText();
                JOptionPane.showMessageDialog(this, (hint == null || hint.isBlank()) ? "Voice list is empty. Please check your API key/settings." : hint, "No Voices", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        List<String> curated = availableVoices.stream().limit(10).collect(Collectors.toList());
        JPopupMenu menu = new JPopupMenu();
        for (String v : curated) {
            JMenuItem item = new JMenuItem(v);
            item.addActionListener(e -> {
                if (narrator) narratorVoiceField.setText(v); else playerVoiceField.setText(v);
            });
            menu.add(item);
        }
        if (availableVoices.size() > curated.size()) {
            menu.addSeparator();
            JMenuItem more = new JMenuItem("More…");
            more.addActionListener(e -> openVoicePickerDialog(narrator));
            menu.add(more);
        }
        // Always allow manual entry
        menu.addSeparator();
        JMenuItem custom = new JMenuItem("Custom…");
        custom.addActionListener(e -> {
            String msg = "Enter a voice (Azure short name or 11Labs in 'Name (voice_id)' format).";
            String current = narrator ? narratorVoiceField.getText() : playerVoiceField.getText();
            String input = (String) JOptionPane.showInputDialog(this, msg, "Custom Voice", JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input != null && !input.trim().isEmpty()) {
                if (narrator) narratorVoiceField.setText(input.trim()); else playerVoiceField.setText(input.trim());
            }
        });
        menu.add(custom);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void openVoicePickerDialog(boolean narrator) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Select Voice", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(8, 8));
        JPanel content = new JPanel(new BorderLayout(5, 5));
        JTextField search = new JTextField();
        DefaultListModel<String> model = new DefaultListModel<>();
        availableVoices.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(12);
        JScrollPane scroll = new JScrollPane(list);
        content.add(new JLabel("Search:"), BorderLayout.WEST);
        content.add(search, BorderLayout.CENTER);
        dlg.add(content, BorderLayout.NORTH);
        dlg.add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(ok);
        dlg.add(buttons, BorderLayout.SOUTH);

        Runnable applySelection = () -> {
            String sel = list.getSelectedValue();
            if (sel != null && !sel.isEmpty()) {
                if (narrator) narratorVoiceField.setText(sel); else playerVoiceField.setText(sel);
                dlg.dispose();
            }
        };

        ok.addActionListener(e -> applySelection.run());
        cancel.addActionListener(e -> dlg.dispose());
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) applySelection.run(); }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String q = search.getText().toLowerCase();
                model.clear();
                for (String v : availableVoices) {
                    if (v.toLowerCase().contains(q)) model.addElement(v);
                }
            }
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });

        dlg.setSize(420, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void loadVoicesAsync() {
        String key = new String(azureKeyField.getPassword()).trim();
        String region = (String) azureRegionCombo.getSelectedItem();
        if (key.isEmpty() || region == null || region.isBlank()) {
            voicesStatusLabel.setText("Enter Azure key and region to load voices");
            return;
        }
        voicesStatusLabel.setText("Loading Azure voices…");

        new Thread(() -> {
            try {
                com.example.osrstts.tts.AzureSpeechTtsClient client = new com.example.osrstts.tts.AzureSpeechTtsClient(key, region, "riff-24khz-16bit-mono-pcm");
                String json = client.listVoicesSample();
                if (json != null && json.startsWith("Voices list failed")) {
                    String msg = json; // includes status
                    SwingUtilities.invokeLater(() -> voicesStatusLabel.setText(msg));
                    return;
                }
                com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                List<String> names = new ArrayList<>();
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : root) {
                        com.fasterxml.jackson.databind.JsonNode shortName = n.get("ShortName");
                        if (shortName != null && shortName.isTextual()) { names.add(shortName.asText()); }
                    }
                }
                if (names.isEmpty()) { names.add("en-US-JennyNeural"); names.add("en-US-DavisNeural"); }
                names = names.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
                List<String> finalNames = names;
                SwingUtilities.invokeLater(() -> {
                    availableVoices.clear();
                    availableVoices.addAll(finalNames);
                    voicesStatusLabel.setText("Voices: " + availableVoices.size());
                    if (narratorVoiceField.getText().isBlank() && !availableVoices.isEmpty()) narratorVoiceField.setText(availableVoices.get(0));
                    if (playerVoiceField.getText().isBlank() && availableVoices.size() > 1) playerVoiceField.setText(availableVoices.get(1));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    voicesStatusLabel.setText("Load failed: " + ex.getMessage());
                });
            }
        }, "azure-voices-loader").start();
    }

    private void load11VoicesAsync() {
        String key = new String(elevenKeyField.getPassword()).trim();
        if (key.isEmpty()) {
            voicesStatusLabel.setText("Enter ElevenLabs API key to load voices");
            return;
        }
        voicesStatusLabel.setText("Loading 11Labs voices…");

        new Thread(() -> {
            try {
                com.example.osrstts.tts.ElevenLabsTtsClient client = new com.example.osrstts.tts.ElevenLabsTtsClient(key, elevenModelField.getText().trim(), "wav_22050");
                String json = client.listVoicesSample();
                if (json != null && json.startsWith("Voices list failed")) {
                    String msg = json; // includes status
                    // Fallback curated list for common public voices
                    List<String> fallback = defaultElevenVoices();
                    SwingUtilities.invokeLater(() -> {
                        availableVoices.clear();
                        availableVoices.addAll(fallback);
                        voicesStatusLabel.setText(msg + " — using fallback list");
                        if (!availableVoices.isEmpty()) {
                            if (narratorVoiceField.getText().isBlank()) narratorVoiceField.setText(availableVoices.get(0));
                            if (playerVoiceField.getText().isBlank()) playerVoiceField.setText(availableVoices.get(Math.min(1, availableVoices.size()-1)));
                        }
                    });
                    return;
                }
                com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                List<String> names = new ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode arr = root.get("voices");
                if (arr != null && arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode v : arr) {
                        String name = v.path("name").asText("");
                        String id = v.path("voice_id").asText("");
                        if (!name.isEmpty() && !id.isEmpty()) names.add(name + " (" + id + ")");
                    }
                }
                names = names.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
                List<String> finalNames = names;
                SwingUtilities.invokeLater(() -> {
                    availableVoices.clear();
                    availableVoices.addAll(finalNames);
                    voicesStatusLabel.setText("Voices: " + availableVoices.size());
                    if (!availableVoices.isEmpty()) {
                        if (narratorVoiceField.getText().isBlank()) narratorVoiceField.setText(availableVoices.get(0));
                        if (playerVoiceField.getText().isBlank()) playerVoiceField.setText(availableVoices.get(Math.min(1, availableVoices.size()-1)));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    voicesStatusLabel.setText("Load failed: " + ex.getMessage());
                });
            }
        }, "eleven-voices-loader").start();
    }

    private List<String> defaultElevenVoices() {
        List<String> list = new ArrayList<>();
        // Small curated set of public/sample voices published by ElevenLabs
        list.add("Rachel (21m00Tcm4TlvDq8ikWAM)");
        list.add("Adam (pNInz6obpgDQGcFmaJgB)");
        list.add("Antoni (ErXwobaYiN019PkySvjV)");
        list.add("Bella (EXAVITQu4vr4xnSDxMaL)");
        list.add("Domi (AZnzlk1XvdvUeBnXmlld)");
        list.add("Elli (MF3mGyEYCl7XYWbV9V6O)");
        list.add("Josh (TxGEqnHWrfWFTfGW9XjX)");
        list.add("Arnold (VR6AewLTigWG4xSOukaG)");
        list.add("Dorothy (ThT5KcBeYPX3keUQqHPh)");
        list.add("Sam (yoZ06aMxZJJ28mfd3POQ)");
        return list;
    }

    private void saveSettings() {
        String key = new String(azureKeyField.getPassword()).trim();
        String region = (String) azureRegionCombo.getSelectedItem();
        String narrator = narratorVoiceField.getText();
        String player = playerVoiceField.getText();
        boolean enabled = enabledCheckbox.isSelected();
        boolean narratorEnabled = narratorEnabledCheckbox.isSelected();
        OsrsTtsRlConfig.Provider provider = (OsrsTtsRlConfig.Provider) providerCombo.getSelectedItem();
        String elevenKey = new String(elevenKeyField.getPassword()).trim();
        String elevenModel = elevenModelField.getText().trim();

        if (configManager != null) {
            // Azure
            configManager.setConfiguration("osrs-tts", "azureKey", key);
            if (region != null) {
                configManager.setConfiguration("osrs-tts", "azureRegion", region);
                try { OsrsTtsRlConfig.AzureRegion enumVal = OsrsTtsRlConfig.AzureRegion.valueOf(region); configManager.setConfiguration("osrs-tts", "azureRegionSelect", enumVal); } catch (IllegalArgumentException ignored) {}
            }
            // Voices
            if (narrator != null && !narrator.isEmpty()) configManager.setConfiguration("osrs-tts", "narratorVoice", narrator);
            if (player != null && !player.isEmpty()) configManager.setConfiguration("osrs-tts", "playerVoice", player);
            // Flags
            configManager.setConfiguration("osrs-tts", "narratorEnabled", narratorEnabled);
            configManager.setConfiguration("osrs-tts", "enabled", enabled);
            if (provider != null) configManager.setConfiguration("osrs-tts", "provider", provider.name());
            // ElevenLabs
            configManager.setConfiguration("osrs-tts", "elevenKey", elevenKey);
            configManager.setConfiguration("osrs-tts", "elevenModel", elevenModel);
        }

        if (plugin != null && plugin.config != null) {
            // Azure
            plugin.config.setAzureKey(key);
            if (region != null) plugin.config.setAzureRegion(region);
            // Voices
            if (narrator != null && !narrator.isEmpty()) plugin.config.setNarratorVoice(narrator);
            if (player != null && !player.isEmpty()) plugin.config.setPlayerVoice(player);
            // Flags
            plugin.config.setNarratorEnabled(narratorEnabled);
            if (provider != null) plugin.config.setProvider(provider.name());
            // ElevenLabs
            plugin.config.setElevenKey(elevenKey);
            plugin.config.setElevenModel(elevenModel);
        }
    }

    private void openLoreSelectorDialog() {
        // Prepare tag list (popular lore + factions + genders)
        java.util.List<String> tags = new ArrayList<>(Arrays.asList(
                "royalty","bandit","pirate","dwarf","goblin","gnome","elf","troll","ogre","giant",
                "wizard","guard","monk","nun","druid","ranger","hunter","archer","barbarian","sailor","seaman",
                "fisherman","miner","smith","vampire","werewolf","ghost","skeleton","zombie","shade","undead",
                "fremennik","morytania","desert","menaphite","al kharid","kandarin","asgarnia","misthalin","varrock",
                "lumbridge","falador","ardougne","kourend","shayzien","arceuus","hosidius","lovakengj","piscarilius",
                "karamja","tzhaar","tirannwn","prifddinas","wilderness","khazard","zamorak","saradomin","guthix",
                "bandos","armadyl","male","female","kid"
        ));
        // Load existing mappings from osrs-voices.json (working dir)
        Map<String,String> current = new HashMap<>();
        File f = new File("osrs-voices.json");
        if (f.exists()) {
            try {
                String json = Files.readString(f.toPath());
                com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                com.fasterxml.jackson.databind.JsonNode t = root.path("tags");
                if (t.isObject()) {
                    java.util.Iterator<String> it = t.fieldNames();
                    while (it.hasNext()) {
                        String k = it.next();
                        tags.add(k); // ensure appear
                        current.put(k.toLowerCase(Locale.ROOT), t.path(k).asText(""));
                    }
                }
            } catch (Exception ignored) {}
        }
        // De-dup and sort tags
        tags = tags.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().sorted().collect(Collectors.toList());

        // Build dialog UI
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Lore Voice Selector", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(8, 8));
        JPanel listPanel = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(listPanel);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4); c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;

        java.util.Map<String,JTextField> editors = new LinkedHashMap<>();
        int row = 0;
        for (String tag : tags) {
            c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0.0;
            listPanel.add(new JLabel(tag), c);
            c.gridx = 1; c.weightx = 1.0;
            JTextField field = new JTextField(current.getOrDefault(tag, ""));
            field.setEditable(false);
            editors.put(tag, field);
            listPanel.add(field, c);
            c.gridx = 2; c.weightx = 0.0;
            JButton choose = new JButton("Choose…");
            choose.addActionListener(e -> openVoiceMenu(choose, field));
            listPanel.add(choose, c);
            row++;
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        buttons.add(cancel); buttons.add(save);

        dlg.add(new JLabel("Assign voices per lore tag. For ElevenLabs, pick entries like 'Name (voice_id)'."), BorderLayout.NORTH);
        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(e -> dlg.dispose());
        save.addActionListener(e -> {
            try {
                // Read existing JSON or start fresh
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.node.ObjectNode root;
                if (f.exists()) {
                    try {
                        String json = Files.readString(f.toPath());
                        root = (com.fasterxml.jackson.databind.node.ObjectNode) om.readTree(json);
                    } catch (Exception ex) {
                        root = om.createObjectNode();
                    }
                } else {
                    root = om.createObjectNode();
                }
                if (!root.has("npcExact")) root.putObject("npcExact");
                if (!root.has("npcRegex")) root.putObject("npcRegex");
                com.fasterxml.jackson.databind.node.ObjectNode tagsNode = root.has("tags") && root.get("tags").isObject()
                        ? (com.fasterxml.jackson.databind.node.ObjectNode) root.get("tags")
                        : om.createObjectNode();
                for (Map.Entry<String,JTextField> en : editors.entrySet()) {
                    String val = en.getValue().getText().trim();
                    if (!val.isEmpty()) tagsNode.put(en.getKey(), val);
                }
                root.set("tags", tagsNode);
                Files.writeString(f.toPath(), om.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(OsrsTtsConfigPanel.this, "Lore voices saved to osrs-voices.json", "Saved", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(OsrsTtsConfigPanel.this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dlg.setSize(720, 560);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void openVoiceMenu(JButton anchor, JTextField targetField) {
        if (availableVoices.isEmpty()) {
            autoLoadVoicesForProvider();
            if (availableVoices.isEmpty()) {
                JOptionPane.showMessageDialog(this, voicesStatusLabel.getText(), "No Voices", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        List<String> curated = availableVoices.stream().limit(10).collect(Collectors.toList());
        JPopupMenu menu = new JPopupMenu();
        for (String v : curated) {
            JMenuItem item = new JMenuItem(v);
            item.addActionListener(e -> targetField.setText(v));
            menu.add(item);
        }
        if (availableVoices.size() > curated.size()) {
            menu.addSeparator();
            JMenuItem more = new JMenuItem("More…");
            more.addActionListener(e -> {
                String chosen = openVoicePickerAndReturn();
                if (chosen != null) targetField.setText(chosen);
            });
            menu.add(more);
        }
        menu.addSeparator();
        JMenuItem custom = new JMenuItem("Custom…");
        custom.addActionListener(e -> {
            String input = (String) JOptionPane.showInputDialog(this, "Enter custom voice (Azure short name or 11Labs 'Name (id)'):", "Custom Voice", JOptionPane.PLAIN_MESSAGE, null, null, targetField.getText());
            if (input != null && !input.trim().isEmpty()) targetField.setText(input.trim());
        });
        menu.add(custom);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private String openVoicePickerAndReturn() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Select Voice", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(8, 8));
        JTextField search = new JTextField();
        DefaultListModel<String> model = new DefaultListModel<>();
        availableVoices.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dlg.add(search, BorderLayout.NORTH);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        final String[] result = new String[1];
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK"); JButton cancel = new JButton("Cancel");
        buttons.add(cancel); buttons.add(ok);
        dlg.add(buttons, BorderLayout.SOUTH);
        Runnable apply = () -> { result[0] = list.getSelectedValue(); dlg.dispose(); };
        ok.addActionListener(e -> apply.run());
        cancel.addActionListener(e -> { result[0] = null; dlg.dispose(); });
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String q = search.getText().toLowerCase(Locale.ROOT);
                model.clear();
                for (String v : availableVoices) if (v.toLowerCase(Locale.ROOT).contains(q)) model.addElement(v);
            }
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        dlg.setSize(420, 360); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
        return result[0];
    }

    private DocumentListener reloadOnChange() {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { scheduleAutoLoad(); }
            public void removeUpdate(DocumentEvent e) { scheduleAutoLoad(); }
            public void changedUpdate(DocumentEvent e) { scheduleAutoLoad(); }
        };
    }

    private void scheduleAutoLoad() {
        if (debounceTimer != null && debounceTimer.isRunning()) debounceTimer.stop();
        debounceTimer = new javax.swing.Timer(600, e -> autoLoadVoicesForProvider());
        debounceTimer.setRepeats(false);
        debounceTimer.start();
    }

    private void autoLoadVoicesForProvider() {
        OsrsTtsRlConfig.Provider sel = (OsrsTtsRlConfig.Provider) providerCombo.getSelectedItem();
        if (sel == null) return;
        if (sel == OsrsTtsRlConfig.Provider.ElevenLabs) {
            String k = new String(elevenKeyField.getPassword()).trim();
            if (!k.isEmpty()) {
                load11VoicesAsync();
            } else {
                voicesStatusLabel.setText("Enter ElevenLabs API key to load voices");
            }
        } else {
            String k = new String(azureKeyField.getPassword()).trim();
            String r = (String) azureRegionCombo.getSelectedItem();
            if (!k.isEmpty() && r != null && !r.isBlank()) {
                loadVoicesAsync();
            } else {
                voicesStatusLabel.setText("Enter Azure key and region to load voices");
            }
        }
    }

    private void onProviderChanged() {
        OsrsTtsRlConfig.Provider sel = (OsrsTtsRlConfig.Provider) providerCombo.getSelectedItem();
        if (sel == null) return;
        // Toggle sections
        boolean eleven = sel == OsrsTtsRlConfig.Provider.ElevenLabs;
        setAzureSectionVisible(!eleven);
        setElevenSectionVisible(eleven);
        // Persist provider immediately so the plugin can rebuild runtime via ConfigChanged
        try {
            if (configManager != null) {
                configManager.setConfiguration("osrs-tts", "provider", sel.name());
            }
        } catch (Exception ignored) {}
        try {
            if (plugin != null && plugin.config != null) {
                plugin.config.setProvider(sel.name());
            }
        } catch (Exception ignored) {}
        // Auto-load voices for the chosen provider if possible
        scheduleAutoLoad();
    }

    private void setAzureSectionVisible(boolean vis) {
        if (azureHeaderLabel != null) azureHeaderLabel.setVisible(vis);
        if (azureKeyLabel != null) azureKeyLabel.setVisible(vis);
        if (azureKeyRow != null) azureKeyRow.setVisible(vis);
        if (azureRegionLabel != null) azureRegionLabel.setVisible(vis);
        if (azureRegionCombo != null) azureRegionCombo.setVisible(vis);
        revalidate(); repaint();
    }

    private void setElevenSectionVisible(boolean vis) {
        if (elevenHeaderLabel != null) elevenHeaderLabel.setVisible(vis);
        if (elevenKeyLabel != null) elevenKeyLabel.setVisible(vis);
        if (elevenKeyRow != null) elevenKeyRow.setVisible(vis);
        if (elevenModelLabel != null) elevenModelLabel.setVisible(vis);
        if (elevenModelField != null) elevenModelField.setVisible(vis);
        revalidate(); repaint();
    }
}
