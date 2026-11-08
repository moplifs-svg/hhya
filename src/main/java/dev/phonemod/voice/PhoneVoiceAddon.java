package dev.phonemod.voice;

import dev.phonemod.PhoneMod;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries phone calls over Plasmo Voice.
 *
 * While a call is active, whatever one participant says is also forwarded to the other
 * participant through a direct source on the "phone" source line. The speak events are
 * only observed, never cancelled, so players standing next to the speaker still hear
 * them through normal proximity voice.
 */
@Addon(
        id = "pv-addon-phonemod",
        name = "Phone Mod",
        version = "1.0.0",
        authors = {"PhoneMod"}
)
public final class PhoneVoiceAddon implements AddonInitializer {
    public static final PhoneVoiceAddon INSTANCE = new PhoneVoiceAddon();

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private volatile ServerSourceLine line;

    /** speaker uuid -> direct source attached to the OTHER participant */
    private final Map<UUID, ServerDirectSource> routes = new ConcurrentHashMap<>();

    @Override
    public void onAddonInitialize() {
        // own source line: gets its own icon and its own volume slider in the Volume tab
        line = voiceServer.getSourceLineManager().createBuilder(
                this,
                "phone",
                "Phone",
                "phonemod:textures/icons/phone.png",
                20
        ).build();

        // LOWEST so the events are seen before Plasmo Voice's own proximity handling
        voiceServer.getEventBus().register(this, PlayerSpeakEvent.class, EventPriority.LOWEST, this::onSpeak);
        voiceServer.getEventBus().register(this, PlayerSpeakEndEvent.class, EventPriority.LOWEST, this::onSpeakEnd);
        PhoneMod.LOGGER.info("Phone voice addon ready");
    }

    @Override
    public void onAddonShutdown() {
        for (ServerDirectSource s : routes.values()) s.remove();
        routes.clear();
    }

    public boolean hasVoiceChat(UUID player) {
        if (voiceServer == null) return false;
        return voiceServer.getPlayerManager().getPlayerById(player)
                .map(p -> p.hasVoiceChat())
                .orElse(false);
    }

    public synchronized boolean startCall(UUID a, UUID b) {
        if (voiceServer == null || line == null) return false;
        VoiceServerPlayer va = voiceServer.getPlayerManager().getPlayerById(a).orElse(null);
        VoiceServerPlayer vb = voiceServer.getPlayerManager().getPlayerById(b).orElse(null);
        if (va == null || vb == null) return false;

        stopCall(a, b);
        routes.put(a, line.createDirectSource(vb, false)); // a speaks, b hears
        routes.put(b, line.createDirectSource(va, false)); // b speaks, a hears
        return true;
    }

    public synchronized void stopCall(UUID a, UUID b) {
        ServerDirectSource sa = routes.remove(a);
        if (sa != null) sa.remove();
        ServerDirectSource sb = routes.remove(b);
        if (sb != null) sb.remove();
    }

    private void onSpeak(PlayerSpeakEvent event) {
        ServerDirectSource source = routes.get(event.getPlayer().getInstance().getUUID());
        if (source == null) return;
        var packet = event.getPacket();
        source.sendAudioFrame(packet.getData(), packet.getSequenceNumber());
    }

    private void onSpeakEnd(PlayerSpeakEndEvent event) {
        ServerDirectSource source = routes.get(event.getPlayer().getInstance().getUUID());
        if (source == null) return;
        var packet = event.getPacket();
        source.sendAudioEnd(packet.getSequenceNumber());
    }
}
