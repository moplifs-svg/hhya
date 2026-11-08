package dev.phonemod;

import dev.phonemod.call.CallManager;
import dev.phonemod.data.PhoneData;
import dev.phonemod.data.PhoneData.Conv;
import dev.phonemod.net.PhoneNet;
import dev.phonemod.net.PhoneSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/** Server side logic of the phone: contacts, messages, group chats, and sending screen data. */
public final class PhoneService {
    private static final int MAX_TEXT = 256;
    private static final int MAX_GROUP_MEMBERS = 20;
    private static final int SENT_MESSAGES = 60;

    // ---------- opening / pushing ----------

    public static void open(ServerPlayer p) {
        if (PhoneConfig.REQUIRE_PHONE_ITEM.get() && !CallManager.hasPhone(p)) {
            p.sendSystemMessage(Component.literal("You need a phone in your inventory."));
            return;
        }
        push(p, true, "", "");
    }

    public static void push(ServerPlayer p, boolean open, String focus, String status) {
        PhoneData d = PhoneData.get(p.server);
        PhoneSnapshot s = build(p, d, open, focus, status);
        PhoneNet.CH.send(PacketDistributor.PLAYER.with(() -> p), s);
    }

    public static void pushIfOnline(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) push(sp, false, "", "");
    }

    /** One line of feedback shown on the phone screen. */
    public static void status(ServerPlayer p, String msg) {
        push(p, false, "", msg);
    }

    private static PhoneSnapshot build(ServerPlayer p, PhoneData d, boolean open, String focus, String status) {
        UUID me = p.getUUID();
        PhoneSnapshot s = new PhoneSnapshot();
        s.open = open;
        s.focus = focus;
        s.status = status;
        s.myNumber = d.numberOf(me);
        s.callState = CallManager.stateFor(me);
        UUID other = CallManager.otherOf(me);
        s.callLabel = other == null ? "" : d.label(me, other);

        List<PhoneSnapshot.Contact> cs = new ArrayList<>();
        d.contactsOf(me).forEach((n, name) -> cs.add(new PhoneSnapshot.Contact(n, name)));
        cs.sort(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)));
        s.contacts.addAll(cs);

        for (Conv c : d.convsOf(me)) {
            List<String> others = new ArrayList<>();
            for (UUID m : c.members) if (!m.equals(me)) others.add(d.label(me, m));
            String title = c.group ? c.name : (others.isEmpty() ? "Chat" : others.get(0));
            List<PhoneSnapshot.Msg> msgs = new ArrayList<>();
            int from = Math.max(0, c.msgs.size() - SENT_MESSAGES);
            for (PhoneData.StoredMsg m : c.msgs.subList(from, c.msgs.size())) {
                boolean mine = m.sender.equals(me);
                msgs.add(new PhoneSnapshot.Msg(mine, mine ? "" : d.label(me, m.sender), m.text));
            }
            s.convs.add(new PhoneSnapshot.Conv(c.id, title, c.group, String.join(", ", others), msgs));
        }
        return s;
    }

    // ---------- contacts ----------

    public static void addContact(ServerPlayer p, String name, String number) {
        PhoneData d = PhoneData.get(p.server);
        String n = PhoneData.digits(number);
        name = name.strip();
        if (name.length() > 16) name = name.substring(0, 16);
        if (name.isEmpty()) { status(p, "Enter a name."); return; }
        if (n.length() != 10) { status(p, "A number has 10 digits."); return; }
        UUID owner = d.ownerOf(n);
        if (owner == null) { status(p, "No phone has that number."); return; }
        if (owner.equals(p.getUUID())) { status(p, "That is your own number."); return; }
        d.addContact(p.getUUID(), n, name);
        status(p, "Contact saved.");
    }

    // ---------- messages ----------

    public static void sendMessage(ServerPlayer p, String convId, String text) {
        text = text.strip();
        if (text.isEmpty()) return;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        PhoneData d = PhoneData.get(p.server);
        Conv c = d.conv(convId);
        if (c == null || !c.members.contains(p.getUUID())) return;
        long now = System.currentTimeMillis();
        c.msgs.add(new PhoneData.StoredMsg(p.getUUID(), now, text));
        while (c.msgs.size() > PhoneData.MAX_STORED_MESSAGES) c.msgs.remove(0);
        c.lastTime = now;
        d.setDirty();
        for (UUID m : c.members) pushIfOnline(p.server, m);
    }

    public static void startChat(ServerPlayer p, String number) {
        PhoneData d = PhoneData.get(p.server);
        String n = PhoneData.digits(number);
        if (n.length() != 10) { status(p, "A number has 10 digits."); return; }
        UUID other = d.ownerOf(n);
        if (other == null) { status(p, "No phone has that number."); return; }
        if (other.equals(p.getUUID())) { status(p, "That is your own number."); return; }
        Conv c = d.findDirect(p.getUUID(), other);
        if (c == null) c = d.createConv("", false, List.of(p.getUUID(), other));
        push(p, false, c.id, "");
        pushIfOnline(p.server, other);
    }

    public static void createGroup(ServerPlayer p, String name, String numbers) {
        PhoneData d = PhoneData.get(p.server);
        name = name.strip();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) { status(p, "Enter a group name."); return; }

        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(p.getUUID());
        for (String part : numbers.split("[,;\\s]+")) {
            String n = PhoneData.digits(part);
            if (n.isEmpty()) continue;
            if (n.length() != 10) { status(p, "Not a 10 digit number: " + part); return; }
            UUID u = d.ownerOf(n);
            if (u == null) { status(p, "No phone has the number " + PhoneData.format(n)); return; }
            members.add(u);
        }
        if (members.size() < 2) { status(p, "Add at least one other number."); return; }
        if (members.size() > MAX_GROUP_MEMBERS) { status(p, "A group can have " + MAX_GROUP_MEMBERS + " people at most."); return; }

        Conv c = d.createConv(name, true, members);
        push(p, false, c.id, "");
        for (UUID m : members) if (!m.equals(p.getUUID())) pushIfOnline(p.server, m);
    }

    public static void addMember(ServerPlayer p, String convId, String number) {
        PhoneData d = PhoneData.get(p.server);
        Conv c = d.conv(convId);
        if (c == null || !c.group || !c.members.contains(p.getUUID())) return;
        String n = PhoneData.digits(number);
        if (n.length() != 10) { status(p, "A number has 10 digits."); return; }
        UUID u = d.ownerOf(n);
        if (u == null) { status(p, "No phone has that number."); return; }
        if (c.members.contains(u)) { status(p, "Already in this group."); return; }
        if (c.members.size() >= MAX_GROUP_MEMBERS) { status(p, "This group is full."); return; }
        c.members.add(u);
        d.setDirty();
        push(p, false, c.id, "Added " + d.label(p.getUUID(), u) + ".");
        for (UUID m : c.members) if (!m.equals(p.getUUID())) pushIfOnline(p.server, m);
    }

    private PhoneService() {}
}
