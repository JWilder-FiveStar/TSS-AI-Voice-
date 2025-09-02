package com.example.osrstts.ui;

import com.example.osrstts.OsrsTtsConfigPanel;
import com.example.osrstts.OsrsTtsPlugin;
import com.example.osrstts.OsrsTtsRlConfig;
import net.runelite.client.config.ConfigManager;

import javax.swing.*;
import java.awt.*;

public class AdvancedSettingsDialog extends JDialog {
    public AdvancedSettingsDialog(Window owner, OsrsTtsPlugin plugin, OsrsTtsRlConfig config, ConfigManager configManager) {
        super(owner, "OSRS TTS — Advanced Settings", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        // Reuse the existing rich panel inside the dialog
        OsrsTtsConfigPanel inner = new OsrsTtsConfigPanel(plugin, config, configManager);
        add(inner, BorderLayout.CENTER);
        setSize(760, 720);
        setLocationRelativeTo(owner);
    }
}
