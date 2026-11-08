package dev.phonemod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Saved with the world: phone numbers, contacts, conversations. */
public class PhoneData extends SavedData {
    private static final String NAME = "phonemod_data";
    public static final int MAX_STORED_MESSAGES = 200;

    public static final class StoredMsg {
        public final UUID sender;
        public final long time;
        public final String text;

        public StoredMsg(UUID sender, long time, String text) {
            this.sender = sender;
            this.time = time;
            this.text = text;
        }
    }

    public static final class Conv {
        public final String id;
        public String name = "";
        public boolean group;
        public long lastTime;
        public final List<UUID> members = new ArrayList<>();
        public final List<StoredMsg> msgs = new ArrayList<>();

        public Conv(String id) {
            this.id = id;
        }
    }

    private final Map<UUID, String> numbers = new HashMap<>();
    private final Map<String, UUID> owners = new HashMap<>();
    private final Map<UUID, LinkedHashMap<String, String>> contacts = new HashMap<>();
    private final Map<String, Conv> convs = new LinkedHashMap<>();

    public static PhoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PhoneData::load, PhoneData::new, NAME);
    }

    // ---------- numbers ----------

    public static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    public static String format(String n) {
        return n.length() == 10 ? n.substring(0, 3) + "-" + n.substring(3, 6) + "-" + n.substring(6) : n;
    }

    /** The 10 digit number of a player, created on first use. */
    public String numberOf(UUID u) {
        String n = numbers.get(u);
        if (n == null) {
            do {
                n = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
            } while (owners.containsKey(n));
            numbers.put(u, n);
            owners.put(n, u);
            setDirty();
        }
        return n;
    }

    public UUID ownerOf(String number) {
        return owners.get(number);
    }

    // ---------- contacts ----------

    public Map<String, String> contactsOf(UUID owner) {
        return contacts.getOrDefault(owner, new LinkedHashMap<>());
    }

    public void addContact(UUID owner, String number, String name) {
        contacts.computeIfAbsent(owner, k -> new LinkedHashMap<>()).put(number, name);
        setDirty();
    }

    public boolean hasContact(UUID owner, String number) {
        return contactsOf(owner).containsKey(number);
    }

    /** How `viewer` sees `other`: the contact name if saved, otherwise the formatted number. */
    public String label(UUID viewer, UUID other) {
        String n = numberOf(other);
        String name = contactsOf(viewer).get(n);
        return name != null ? name : format(n);
    }

    // ---------- conversations ----------

    public Conv conv(String id) {
        return convs.get(id);
    }

    public List<Conv> convsOf(UUID u) {
        List<Conv> r = new ArrayList<>();
        for (Conv c : convs.values()) if (c.members.contains(u)) r.add(c);
        r.sort(Comparator.comparingLong((Conv c) -> c.lastTime).reversed());
        return r;
    }

    public Conv findDirect(UUID a, UUID b) {
        for (Conv c : convs.values()) {
            if (!c.group && c.members.size() == 2 && c.members.contains(a) && c.members.contains(b)) return c;
        }
        return null;
    }

    public Conv createConv(String name, boolean group, Collection<UUID> members) {
        Conv c = new Conv(UUID.randomUUID().toString());
        c.name = name;
        c.group = group;
        c.members.addAll(members);
        c.lastTime = System.currentTimeMillis();
        convs.put(c.id, c);
        setDirty();
        return c;
    }

    // ---------- save / load ----------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag nl = new ListTag();
        numbers.forEach((u, n) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            c.putString("n", n);
            nl.add(c);
        });
        tag.put("numbers", nl);

        ListTag cl = new ListTag();
        contacts.forEach((u, map) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            ListTag l = new ListTag();
            map.forEach((n, name) -> {
                CompoundTag e = new CompoundTag();
                e.putString("n", n);
                e.putString("name", name);
                l.add(e);
            });
            c.put("l", l);
            cl.add(c);
        });
        tag.put("contacts", cl);

        ListTag vl = new ListTag();
        for (Conv conv : convs.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", conv.id);
            c.putString("name", conv.name);
            c.putBoolean("group", conv.group);
            c.putLong("last", conv.lastTime);
            ListTag ml = new ListTag();
            for (UUID m : conv.members) {
                CompoundTag e = new CompoundTag();
                e.putUUID("u", m);
                ml.add(e);
            }
            c.put("members", ml);
            ListTag gl = new ListTag();
            for (StoredMsg m : conv.msgs) {
                CompoundTag e = new CompoundTag();
                e.putUUID("s", m.sender);
                e.putLong("t", m.time);
                e.putString("x", m.text);
                gl.add(e);
            }
            c.put("msgs", gl);
            vl.add(c);
        }
        tag.put("convs", vl);
        return tag;
    }

    public static PhoneData load(CompoundTag tag) {
        PhoneData d = new PhoneData();
        for (Tag t : tag.getList("numbers", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            UUID u = c.getUUID("u");
            String n = c.getString("n");
            d.numbers.put(u, n);
            d.owners.put(n, u);
        }
        for (Tag t : tag.getList("contacts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            for (Tag e : c.getList("l", Tag.TAG_COMPOUND)) {
                CompoundTag ec = (CompoundTag) e;
                map.put(ec.getString("n"), ec.getString("name"));
            }
            d.contacts.put(c.getUUID("u"), map);
        }
        for (Tag t : tag.getList("convs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Conv conv = new Conv(c.getString("id"));
            conv.name = c.getString("name");
            conv.group = c.getBoolean("group");
            conv.lastTime = c.getLong("last");
            for (Tag e : c.getList("members", Tag.TAG_COMPOUND)) conv.members.add(((CompoundTag) e).getUUID("u"));
            for (Tag e : c.getList("msgs", Tag.TAG_COMPOUND)) {
                CompoundTag ec = (CompoundTag) e;
                conv.msgs.add(new StoredMsg(ec.getUUID("s"), ec.getLong("t"), ec.getString("x")));
            }
            d.convs.put(conv.id, conv);
        }
        return d;
    }
}
