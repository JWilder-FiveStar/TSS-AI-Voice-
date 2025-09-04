package com.example.osrstts.dialog;

import com.example.osrstts.voice.VoiceRuntime;
import net.runelite.api.events.ChatMessage;
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
            String npc = chatMessage.getName();
            String msg = chatMessage.getMessage();
            if (npc != null && !npc.isEmpty() && msg != null && !msg.isEmpty()) {
                voiceRuntime.speakNpc(npc, stripTags(msg), Collections.emptySet());
            }
            // TODO: detect quest journal or book narration from appropriate events/widgets;
            // if detected and narrator enabled: voiceRuntime.speakNarrator(text)
        } catch (Exception ignored) {}
    }


    private String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }
}