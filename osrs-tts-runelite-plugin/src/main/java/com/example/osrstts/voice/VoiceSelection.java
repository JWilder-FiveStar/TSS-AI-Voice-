package com.example.osrstts.voice;

/**
 * Provider-agnostic voice selection result.
 */
public class VoiceSelection {
    public final String voiceName;   // e.g., "en-GB-RyanNeural" or Polly voice id
    public final String style;       // e.g., Azure style "chat", may be null
    public final Double rate;        // relative rate (optional)
    public final Double pitch;       // relative pitch (optional)

    public VoiceSelection(String voiceName, String style, Double rate, Double pitch) {
        this.voiceName = voiceName;
        this.style = style;
        this.rate = rate;
        this.pitch = pitch;
    }

    public static VoiceSelection of(String voiceName, String style) {
        return new VoiceSelection(voiceName, style, null, null);
    }
}
