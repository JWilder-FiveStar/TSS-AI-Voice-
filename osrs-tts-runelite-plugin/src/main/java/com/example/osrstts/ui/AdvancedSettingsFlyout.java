package com.example.osrstts.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Advanced settings flyout panel that appears to the right of the main settings.
 * Contains tabbed panels for detailed configuration.
 */
public class AdvancedSettingsFlyout extends JDialog {
    private final JTabbedPane tabs = new JTabbedPane();
    
    public AdvancedSettingsFlyout(Window owner) {
        super(owner, "Advanced TTS Settings", ModalityType.MODELESS);
        setupDialog();
        setupTabs();
        setupCloseOnFocusLoss();
    }
    
    private void setupDialog() {
        setUndecorated(true);
        setResizable(false);
        
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        container.setBackground(UIManager.getColor("Panel.background"));
        
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> setVisible(false));
        
        container.add(tabs, BorderLayout.CENTER);
        container.add(closeBtn, BorderLayout.SOUTH);
        setContentPane(container);
        
        setPreferredSize(new Dimension(450, 600));
        pack();
    }
    
    private void setupTabs() {
        tabs.setBorder(null);
        tabs.addTab("Voices & Tags", new VoicesTagsPanel());
        tabs.addTab("Provider & API", new ProviderApiPanel());
        tabs.addTab("Caching", new CachingPanel());
        tabs.addTab("Safety & Limits", new SafetyPanel());
        tabs.addTab("Import/Export", new ImportExportPanel());
    }
    
    private void setupCloseOnFocusLoss() {
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                // Only hide if the focus didn't go to a child dialog
                if (!isAncestorOf(e.getOppositeWindow())) {
                    setVisible(false);
                }
            }
        });
    }
    
    /**
     * Show the flyout positioned to the right of the given component.
     */
    public void showRightOf(Component relativeTo) {
        Window parentWindow = SwingUtilities.getWindowAncestor(relativeTo);
        if (parentWindow != null) {
            Point parentLocation = parentWindow.getLocationOnScreen();
            Dimension parentSize = parentWindow.getSize();
            
            int x = parentLocation.x + parentSize.width + 10; // 10px gap
            int y = parentLocation.y + 60; // Offset from top
            
            // Ensure the dialog stays on screen
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (x + getWidth() > screenSize.width) {
                x = parentLocation.x - getWidth() - 10; // Show on left instead
            }
            if (y + getHeight() > screenSize.height) {
                y = screenSize.height - getHeight() - 20;
            }
            
            setLocation(x, y);
        }
        
        setVisible(true);
        requestFocusInWindow();
    }
    
    private boolean isAncestorOf(Window window) {
        if (window == null) return false;
        
        Container parent = window.getParent();
        while (parent != null) {
            if (parent == this) return true;
            parent = parent.getParent();
        }
        return false;
    }
}