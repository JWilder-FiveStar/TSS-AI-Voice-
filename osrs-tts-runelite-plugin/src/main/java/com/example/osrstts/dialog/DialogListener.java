package com.example.osrstts.dialog;

import com.example.osrstts.voice.VoiceRuntime;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.Collections;

public class DialogListener {
    private final EventBus eventBus;
    private final VoiceRuntime voiceRuntime;

    public DialogListener(EventBus eventBus, VoiceRuntime voiceRuntime) {
        this.eventBus = eventBus;
        this.voiceRuntime = voiceRuntime;
        if (this.eventBus != null) {
            this.eventBus.register(this);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        try {
            if (chatMessage.getType() == ChatMessageType.NPC_CHAT) {
                String npc = chatMessage.getName();
                String msg = chatMessage.getMessage();
                voiceRuntime.speakNpc(npc, stripTags(msg), Collections.emptySet());
            }
            // TODO: detect quest journal or book narration from appropriate events/widgets;
            // if detected and narrator enabled: voiceRuntime.speakNarrator(text)
        } catch (Exception ignored) {}
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        // Placeholder for periodic checks, e.g., scanning open widgets for book/journal text
    }

    private String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }
}