package dev.phonemod.call;

import dev.phonemod.PhoneConfig;
import dev.phonemod.PhoneMod;
import dev.phonemod.PhoneService;
import dev.phonemod.data.PhoneData;
import dev.phonemod.voice.PhoneVoiceAddon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Server side call state. One call per player at a time. */
public final class CallManager {
    private static final class Call {
        final UUID caller;
        final UUID callee;
        boolean active;
        final long startTick;

        Call(UUID caller, UUID callee, long startTick) {
            this.caller = caller;
            this.callee = callee;
            this.startTick = startTick;
        }
    }

    private static final Map<UUID, Call> CALLS = new HashMap<>();

    // ---------- queries ----------

    /** 0 none, 1 calling, 2 incoming, 3 connected */
    public static int stateFor(UUID u) {
        Call c = CALLS.get(u);
        if (c == null) return 0;
        if (c.active) return 3;
        return c.caller.equals(u) ? 1 : 2;
    }

    public static UUID otherOf(UUID u) {
        Call c = CALLS.get(u);
        if (c == null) return null;
        return c.caller.equals(u) ? c.callee : c.caller;
    }

    public static boolean hasPhone(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(PhoneMod.PHONE.get())) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(PhoneMod.PHONE.get())) return true;
        return false;
    }

    // ---------- actions ----------

    public static void dial(ServerPlayer caller, String rawNumber) {
        MinecraftServer server = caller.server;
        PhoneData d = PhoneData.get(server);
        String number = PhoneData.digits(rawNumber);

        if (CALLS.containsKey(caller.getUUID())) { fail(caller, "You are already in a call."); return; }
        if (PhoneConfig.REQUIRE_PHONE_ITEM.get() && !hasPhone(caller)) { fail(caller, "You need a phone to call."); return; }
        if (number.length() != 10) { fail(caller, "A number has 10 digits."); return; }
        if (!d.hasContact(caller.getUUID(), number)) { fail(caller, "Add this number to your contacts first."); return; }

        UUID targetId = d.ownerOf(number);
        if (targetId == null) { fail(caller, "That number is not in service."); return; }
        if (targetId.equals(caller.getUUID())) { fail(caller, "You cannot call yourself."); return; }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) { fail(caller, "The person you are calling is not available."); return; }
        if (PhoneConfig.REQUIRE_PHONE_ITEM.get() && !hasPhone(target)) { fail(caller, "The person you are calling is not available."); return; }
        if (CALLS.containsKey(targetId)) { fail(caller, "The line is busy."); return; }
        if (!PhoneVoiceAddon.INSTANCE.hasVoiceChat(caller.getUUID())) { fail(caller, "You are not connected to Plasmo Voice."); return; }
        if (!PhoneVoiceAddon.INSTANCE.hasVoiceChat(targetId)) { fail(caller, "The person you are calling has no voice chat connected."); return; }

        Call call = new Call(caller.getUUID(), targetId, server.getTickCount());
        CALLS.put(caller.getUUID(), call);
        CALLS.put(targetId, call);

        MutableComponent msg = Component.literal("Incoming call from " + d.label(targetId, caller.getUUID()) + "  ")
                .withStyle(ChatFormatting.GOLD);
        msg.append(button("[Answer]", "/phone answer", ChatFormatting.GREEN));
        msg.append(Component.literal(" "));
        msg.append(button("[Decline]", "/phone decline", ChatFormatting.RED));
        target.sendSystemMessage(msg);

        caller.sendSystemMessage(Component.literal("Calling " + d.label(caller.getUUID(), targetId) + "...")
                .withStyle(ChatFormatting.YELLOW));
        refresh(call);
    }

    public static void answer(ServerPlayer p) {
        Call c = CALLS.get(p.getUUID());
        if (c == null || c.active || !c.callee.equals(p.getUUID())) {
            p.sendSystemMessage(Component.literal("Nobody is calling you."));
            return;
        }
        if (!PhoneVoiceAddon.INSTANCE.startCall(c.caller, c.callee)) {
            end(p.server, c, "The call could not connect.", "The call could not connect.");
            return;
        }
        c.active = true;
        notifyBoth(p.server, c, "Call connected. Talk with your Plasmo Voice mic key.");
        refresh(c);
    }

    public static void decline(ServerPlayer p) {
        Call c = CALLS.get(p.getUUID());
        if (c == null || c.active || !c.callee.equals(p.getUUID())) {
            p.sendSystemMessage(Component.literal("Nobody is calling you."));
            return;
        }
        end(p.server, c, "Your call was declined.", "Call declined.");
    }

    public static void hangup(ServerPlayer p) {
        Call c = CALLS.get(p.getUUID());
        if (c == null) {
            p.sendSystemMessage(Component.literal("You are not in a call."));
            return;
        }
        boolean mineToCancel = !c.active && c.caller.equals(p.getUUID());
        if (c.active) end(p.server, c, "Call ended.", "Call ended.");
        else if (mineToCancel) end(p.server, c, "Call cancelled.", "Missed call.");
        else decline(p);
    }

    public static void onLogout(ServerPlayer p) {
        Call c = CALLS.get(p.getUUID());
        if (c != null) end(p.server, c, "Call ended.", "Call ended.");
    }

    public static void clear() {
        for (Call c : new HashSet<>(CALLS.values())) PhoneVoiceAddon.INSTANCE.stopCall(c.caller, c.callee);
        CALLS.clear();
    }

    // ---------- ticking ----------

    public static void tick(MinecraftServer server) {
        if (CALLS.isEmpty()) return;
        long now = server.getTickCount();
        long timeout = PhoneConfig.RING_TIMEOUT_SECONDS.get() * 20L;
        boolean checkPhones = PhoneConfig.REQUIRE_PHONE_ITEM.get()
                && PhoneConfig.HANGUP_IF_PHONE_LOST.get() && now % 20 == 0;

        for (Call c : new HashSet<>(CALLS.values())) {
            if (!c.active && now - c.startTick > timeout) {
                end(server, c, "No answer.", "Missed call.");
                continue;
            }
            if (checkPhones) {
                ServerPlayer a = server.getPlayerList().getPlayer(c.caller);
                ServerPlayer b = server.getPlayerList().getPlayer(c.callee);
                if (a == null || b == null || !hasPhone(a) || !hasPhone(b)) {
                    end(server, c, "The call dropped because a phone was lost.", "The call dropped because a phone was lost.");
                }
            }
        }
    }

    // ---------- helpers ----------

    private static void end(MinecraftServer server, Call c, String toCaller, String toCallee) {
        CALLS.remove(c.caller);
        CALLS.remove(c.callee);
        if (c.active) PhoneVoiceAddon.INSTANCE.stopCall(c.caller, c.callee);
        tell(server, c.caller, toCaller);
        tell(server, c.callee, toCallee);
    }

    private static void tell(MinecraftServer server, UUID id, String msg) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p == null) return;
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
        PhoneService.status(p, msg);
    }

    private static void notifyBoth(MinecraftServer server, Call c, String msg) {
        for (UUID id : new UUID[]{c.caller, c.callee}) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GREEN));
        }
    }

    private static void refresh(Call c) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        PhoneService.pushIfOnline(server, c.caller);
        PhoneService.pushIfOnline(server, c.callee);
    }

    private static void fail(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
        PhoneService.status(p, msg);
    }

    private static MutableComponent button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private CallManager() {}
}
