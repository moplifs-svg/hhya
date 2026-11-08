package dev.phonemod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.phonemod.net.PhoneNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.phonemod.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.phonemod");

    private static int ringTick;

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLogout);
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(OPEN_KEY);
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        while (OPEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) PhoneNet.CH.sendToServer(new PhoneNet.Open());
        }

        // ring while somebody is calling
        if (mc.player != null && ClientHooks.snap.callState == 2) {
            int t = ringTick++ % 40;
            if (t == 0 || t == 6) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F));
            }
        } else {
            ringTick = 0;
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientHooks.reset();
    }

    private ClientSetup() {}
}
