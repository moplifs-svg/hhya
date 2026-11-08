package dev.phonemod;

import dev.phonemod.call.CallManager;
import dev.phonemod.data.PhoneData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class ServerEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            // every player gets a 10 digit number the first time they join
            PhoneData.get(sp.server).numberOf(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CallManager.onLogout(sp);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) CallManager.tick(server);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        PhoneCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) {
        CallManager.clear();
    }
}
