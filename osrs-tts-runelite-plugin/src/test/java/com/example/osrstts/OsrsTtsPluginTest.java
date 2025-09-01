package com.example.osrstts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsTtsPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(OsrsTtsPlugin.class);
        RuneLite.main(args);
    }
}
