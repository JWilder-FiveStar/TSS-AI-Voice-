package com.example.osrstts.voice;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.tts.AzureSpeechTtsClient;
import com.example.osrstts.tts.PollyTtsClient;
import com.example.osrstts.tts.TtsClient;
import com.example.osrstts.tts.ElevenLabsTtsClient;
import com.example.osrstts.npc.NpcMetadataService;
import com.example.osrstts.usage.UsageTracker;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import javazoom.jl.player.Player;

public class VoiceRuntime {
    private final OsrsTtsConfig cfg;
    private final VoiceSelector selector;
    private final TtsClient tts;
    private final AudioCache cache;
    private final UsageTracker usage;
    private final VoiceAssignmentStore assignmentStore;
    private final VoiceSelectionPipeline pipeline;
    private String lastPlayKey;
    private long lastPlayAtMs;

    // Known public 11Labs voice as a safe fallback
    private static final String DEFAULT_ELEVEN_VOICE = "Rachel (21m00Tcm4TlvDq8ikWAM)";

    public VoiceRuntime(OsrsTtsConfig cfg) {
        this.cfg = cfg;
    this.selector = new VoiceSelector(
                cfg.getProvider(),
                cfg.getDefaultVoice(),
                cfg.getVoiceMappingFile(),
                cfg.getNpcMaleVoice(),
                cfg.getNpcFemaleVoice(),
                cfg.getNpcKidVoice()
        );
        String prov = cfg.getProvider();
        if ("ElevenLabs".equalsIgnoreCase(prov)) {
            // Use 11Labs and request WAV-compatible output
            this.tts = new ElevenLabsTtsClient(cfg.getElevenKey(), cfg.getElevenModel(), "wav_22050");
        } else if ("Azure".equalsIgnoreCase(prov)) {
            this.tts = new AzureSpeechTtsClient(cfg.getAzureKey(), cfg.getAzureRegion(), cfg.getAudioOutputFormat());
        } else {
            // Prefer WAV for unified playback by switching Polly to PCM if implemented.
            this.tts = new PollyTtsClient();
        }
    this.cache = cfg.isCacheEnabled() ? new AudioCache(cfg.getCacheDir()) : null;
    this.usage = new UsageTracker();
    this.assignmentStore = new VoiceAssignmentStore();
    this.pipeline = new VoiceSelectionPipeline(cfg.getProvider(), selector, assignmentStore, new NpcMetadataService());
    }

    public VoiceAssignmentStore getAssignmentStore() { return assignmentStore; }
    public UsageTracker getUsageTracker() { return usage; }
    public VoiceSelectionPipeline getPipeline() { return pipeline; }

    public void speakNpc(String npcName, String text, Set<String> tags) throws Exception {
    VoiceSelection sel = pipeline.chooseForNpc(null, npcName, text, tags);
        boolean debug = "true".equalsIgnoreCase(System.getProperty("osrs.tts.debug", "false"));
        if ("ElevenLabs".equalsIgnoreCase(cfg.getProvider())) {
            // Ensure sel.voiceName contains a voice_id (format: Name (id))
            if (!looksElevenVoiceId(sel.voiceName)) {
                String nv = cfg.getNarratorVoice();
                String pv = cfg.getPlayerVoice();
                String replacement = looksElevenVoiceId(nv) ? nv : (looksElevenVoiceId(pv) ? pv : null);
                if (replacement == null) replacement = DEFAULT_ELEVEN_VOICE;
                sel = VoiceSelection.of(replacement, sel.style);
                if (debug) System.out.println("TTS NPC ElevenLabs: using voice '" + sel.voiceName + "'");
            }
        }
        if (debug) {
            System.out.println("TTS NPC sel voice=" + sel.voiceName + ", tags=" + (tags == null ? "[]" : tags.toString()) + ", npc='" + npcName + "'");
        }
    String normalized = AudioCache.normalizeText(text);
    String npcKey = (npcName == null || npcName.isEmpty()) ? "npc" : npcName.toLowerCase();
    String cacheKey = cacheKey("npc", sel, normalized);
        if (!shouldPlay(cacheKey)) return;
        try {
            byte[] audio = getOrSynthesize(cacheKey, sel, normalized);
            playAudio(audio);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("Azure TTS error 400")) {
                // Retry with a safe fallback voice based on inferred gender
                boolean female = tags != null && tags.contains("female");
                boolean kid = tags != null && tags.contains("kid");
                String fallbackVoice = kid ? "en-US-JennyNeural" : (female ? "en-US-JennyNeural" : "en-US-GuyNeural");
                if (debug) {
                    System.out.println("TTS NPC Azure 400, retry with fallback voice=" + fallbackVoice + ", npc='" + npcName + "'");
                }
                VoiceSelection fallbackSel = VoiceSelection.of(fallbackVoice, sel.style);
                String fbKey = cacheKey("npc", fallbackSel, text);
                try {
                    byte[] audio = getOrSynthesize(fbKey, fallbackSel, text);
                    playAudio(audio);
                    return;
                } catch (Exception ignored) {
                    // give up to outer layer
                }
            }
            throw ex;
        }
    }

    public void speakNarrator(String text) throws Exception {
    VoiceSelection sel;
        String prov = cfg.getProvider();
        if ("Azure".equalsIgnoreCase(prov)) {
            sel = VoiceSelection.of(cfg.getNarratorVoice(), cfg.getNarratorStyle());
        } else if ("ElevenLabs".equalsIgnoreCase(prov)) {
            String v = cfg.getNarratorVoice();
            if (!looksElevenVoiceId(v)) {
                // Fallback to player voice if narrator isn't a valid 11L voice
                String pv = cfg.getPlayerVoice();
                if (looksElevenVoiceId(pv)) v = pv;
            }
            if (!looksElevenVoiceId(v)) {
                v = DEFAULT_ELEVEN_VOICE;
                if ("true".equalsIgnoreCase(System.getProperty("osrs.tts.debug", "false"))) {
                    System.out.println("TTS Narrator ElevenLabs: falling back to default voice '" + v + "'");
                }
            }
            sel = VoiceSelection.of(v, null);
        } else {
            sel = VoiceSelection.of("Joanna", null); // Polly narrator default
        }
    String normalized = AudioCache.normalizeText(text);
    String cacheKey = cacheKey("narrator", sel, normalized);
        if (!shouldPlay(cacheKey)) return;
    byte[] audio = getOrSynthesize(cacheKey, sel, normalized);
        playAudio(audio);
    }

    public void speakPlayer(String text) throws Exception {
        String v = cfg.getPlayerVoice();
        if ("ElevenLabs".equalsIgnoreCase(cfg.getProvider()) && !looksElevenVoiceId(v)) {
            String nv = cfg.getNarratorVoice();
            v = looksElevenVoiceId(nv) ? nv : DEFAULT_ELEVEN_VOICE;
            if ("true".equalsIgnoreCase(System.getProperty("osrs.tts.debug", "false"))) {
                System.out.println("TTS Player ElevenLabs: using voice '" + v + "'");
            }
        }
    VoiceSelection sel = VoiceSelection.of(v, null);
    String normalized = AudioCache.normalizeText(text);
    String cacheKey = cacheKey("player", sel, normalized);
        if (!shouldPlay(cacheKey)) return;
    byte[] audio = getOrSynthesize(cacheKey, sel, normalized);
        playAudio(audio);
    }

    private boolean shouldPlay(String key) {
        long now = System.currentTimeMillis();
        if (key != null && key.equals(lastPlayKey) && (now - lastPlayAtMs) < 2500) {
            return false;
        }
        lastPlayKey = key;
        lastPlayAtMs = now;
        return true;
    }

    private byte[] getOrSynthesize(String key, VoiceSelection sel, String normalizedText) throws Exception {
        if (cache != null) {
            byte[] hitWav = cache.get(key, "wav");
            if (hitWav != null) return hitWav;
            byte[] hitMp3 = cache.get(key, "mp3");
            if (hitMp3 != null) return hitMp3;
        }
    byte[] data = tts.synthesize(normalizedText, sel);
        usage.addCharacters(normalizedText.length());
        if (cache != null) {
            if (looksRiffWav(data)) cache.put(key, "wav", data);
            else if (looksMp3(data)) cache.put(key, "mp3", data);
            else cache.put(key, "bin", data);
        }
        return data;
    }

    private static boolean looksRiffWav(byte[] data) {
        return data != null && data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    private static boolean looksMp3(byte[] data) {
        return (data != null && data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3')
                || (data != null && data.length >= 2 && (data[0] & 0xFF) == 0xFF && ((data[1] & 0xE0) == 0xE0));
    }

    private String cacheKey(String kind, VoiceSelection sel, String normalizedText) {
        if (cache == null) {
            String base = cfg.getProvider() + "|" + kind + "|" + (sel.voiceName == null ? "auto" : sel.voiceName) + "|" + (sel.style == null ? "-" : sel.style);
            return base + "|" + Integer.toHexString(normalizedText.hashCode());
        }
        String npcKey = kind;
        return cache.keyFor(cfg.getProvider(), sel.voiceName, npcKey, normalizedText, AudioCache.CURRENT_VERSION);
    }

    // Optional tag inference helper for future use
    public java.util.Set<String> inferTags(String npcName) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        if (npcName == null) return tags;
        String n = npcName.toLowerCase();
        if (n.contains("guard")) tags.add("guard");
        if (n.contains("wizard") || n.contains("mage") || n.contains("sorcer")) tags.add("wizard");
        if (n.contains("king") || n.contains("duke") || n.contains("sir") || n.contains("lord") || n.contains("prince")) { tags.add("royalty"); tags.add("male"); }
        if (n.contains("queen") || n.contains("lady") || n.contains("princess") || n.contains("dame") || n.contains("madam") || n.contains("mrs") || n.contains("miss") || n.contains("priestess")) { tags.add("royalty"); tags.add("female"); }
        if (n.contains("vamp")) tags.add("vampire");
        if (n.contains("werewolf")) tags.add("werewolf");
        if (n.contains("ghost") || n.contains("spirit")) tags.add("ghost");
        if (n.contains("skeleton")) tags.add("skeleton");
        if (n.contains("zombie")) tags.add("zombie");
        if (n.contains("shade")) tags.add("shade");
        if (n.contains("undead")) tags.add("undead");
        if (n.contains("monk")) { tags.add("monk"); tags.add("male"); }
        if (n.contains("nun")) { tags.add("nun"); tags.add("female"); }
        if (n.contains("pirate")) tags.add("pirate");
        if (n.contains("dwarf")) tags.add("dwarf");
        if (n.contains("goblin")) tags.add("goblin");
        if (n.contains("gnome")) tags.add("gnome");
        if (n.contains("elf") || n.contains("elven")) tags.add("elf");
        if (n.contains("troll")) tags.add("troll");
        if (n.contains("ogre")) tags.add("ogre");
        if (n.contains("giant")) tags.add("giant");
        if (n.contains("druid")) tags.add("druid");
        if (n.contains("ranger") || n.contains("archer") || n.contains("bowman")) tags.add("ranger");
        if (n.contains("barbarian")) tags.add("barbarian");
        if (n.contains("sailor") || n.contains("seaman")) tags.add("sailor");
        if (n.contains("fisherman")) tags.add("fisherman");
        if (n.contains("miner")) tags.add("miner");
        if (n.contains("smith")) tags.add("smith");
        // Regions and factions (match substrings in names like "Shayzien Guard", etc.)
        if (n.contains("fremennik")) tags.add("fremennik");
        if (n.contains("morytania") || n.contains("canifis") || n.contains("mort")) tags.add("morytania");
        if (n.contains("desert") || n.contains("menaph") || n.contains("al kharid") || n.contains("al-kharid")) tags.add("desert");
        if (n.contains("kandarin") || n.contains("ardougne") || n.contains("catherby") || n.contains("seers")) tags.add("kandarin");
        if (n.contains("asgarnia") || n.contains("falador") || n.contains("taverley") || n.contains("burthorpe")) tags.add("asgarnia");
        if (n.contains("misthalin") || n.contains("varrock") || n.contains("lumbridge")) tags.add("misthalin");
        if (n.contains("kourend") || n.contains("shayzien")) tags.add("shayzien");
        if (n.contains("arceuus")) tags.add("arceuus");
        if (n.contains("hosidius")) tags.add("hosidius");
        if (n.contains("lovakengj")) tags.add("lovakengj");
        if (n.contains("piscarilius")) tags.add("piscarilius");
        if (n.contains("karamja")) tags.add("karamja");
        if (n.contains("tzhaar") || n.contains("tzh")) tags.add("tzhaar");
        if (n.contains("tirannwn") || n.contains("prifddinas") || n.contains("lleyta") || n.contains("isafdar")) tags.add("tirannwn");
        if (n.contains("wilderness") || n.contains("revanant") || n.contains("mage arena")) tags.add("wilderness");
        if (n.contains("khazard")) tags.add("khazard");
        // Gods
        if (n.contains("zamorak")) tags.add("zamorak");
        if (n.contains("saradomin")) tags.add("saradomin");
        if (n.contains("guthix")) tags.add("guthix");
        if (n.contains("bandos")) tags.add("bandos");
        if (n.contains("armadyl")) tags.add("armadyl");
        // Kid/gender
        if (n.contains("boy") || n.contains("girl") || n.contains("child") || n.contains("kid") || n.contains("young")) tags.add("kid");
        if (n.contains("man") || n.contains("brother") || n.endsWith("son")) tags.add("male");
        if (n.contains("woman") || n.contains("sister") || n.endsWith("daughter") || n.contains("lady")) tags.add("female");
        return tags;
    }

    private static boolean looksElevenVoiceId(String name) {
        if (name == null) return false;
        int i = name.lastIndexOf('(');
        int j = name.lastIndexOf(')');
        if (i >= 0 && j > i) {
            String id = name.substring(i + 1, j).trim();
            return id.matches("[A-Za-z0-9]{20,}");
        }
        return name.matches("[A-Za-z0-9]{20,}");
    }

    private void playAudio(byte[] data) throws Exception {
        if (data == null || data.length == 0) return;
        if (looksRiffWav(data)) {
            // WAV path: Clip then fallback to streaming
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data); AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                applyVolume(clip);
                clip.start();
                return;
            } catch (Throwable clipErr) {
                // Fallback: stream via SourceDataLine in background
                new Thread(() -> {
                    try (ByteArrayInputStream bais2 = new ByteArrayInputStream(data); AudioInputStream src = AudioSystem.getAudioInputStream(bais2)) {
                        AudioFormat base = src.getFormat();
                        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                                (base.getSampleRate() <= 0 ? 22050f : base.getSampleRate()),
                                16,
                                Math.max(1, base.getChannels()),
                                Math.max(1, base.getChannels()) * 2,
                                (base.getSampleRate() <= 0 ? 22050f : base.getSampleRate()),
                                false);
                        AudioInputStream pcmStream = AudioSystem.isConversionSupported(target, base)
                                ? AudioSystem.getAudioInputStream(target, src)
                                : src;
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmStream.getFormat());
                        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                            line.open(pcmStream.getFormat());
                            line.start();
                            applyVolume(line);
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = pcmStream.read(buf, 0, buf.length)) > 0) {
                                line.write(buf, 0, n);
                            }
                            line.drain();
                            line.stop();
                        }
                        if (pcmStream != src) try { pcmStream.close(); } catch (IOException ignored) {}
                    } catch (Exception ignored) {
                        // swallow fallback errors to avoid crashing event thread
                    }
                }, "tts-audio-stream").start();
                return;
            }
        }
        if (looksMp3(data)) {
            // MP3 path: use JLayer Player in background
            new Thread(() -> {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                    Player player = new Player(bais);
                    player.play();
                } catch (Exception ignored) {}
            }, "tts-mp3-player").start();
            return;
        }
        // Unknown: attempt generic WAV open; if fails, ignore silently
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            applyVolume(clip);
            clip.start();
        } catch (Throwable ignored) {}
    }

    private void applyVolume(Line line) {
        try {
            int vol = cfg.getVolumePercent();
            float gain = volumePercentToDb(vol);
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl ctrl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float clamped = Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), gain));
                ctrl.setValue(clamped);
            }
        } catch (Throwable ignored) {}
    }

    private static float volumePercentToDb(int percent) {
        float p = Math.max(0, Math.min(100, percent)) / 100f;
        if (p <= 0.0001f) return -80f; // effectively mute
        return (float)(20.0 * Math.log10(p));
    }
}
