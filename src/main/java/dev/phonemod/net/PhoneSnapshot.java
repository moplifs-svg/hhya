package dev.phonemod.net;

import dev.phonemod.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Everything one player's phone needs to draw its screens. Server to client. */
public final class PhoneSnapshot {
    public boolean open;
    public String focus = "";     // conversation id the screen should jump to
    public String status = "";    // one line feedback shown on the phone
    public String myNumber = "";
    public int callState;         // 0 none, 1 calling, 2 incoming, 3 connected
    public String callLabel = "";
    public final List<Contact> contacts = new ArrayList<>();
    public final List<Conv> convs = new ArrayList<>();

    public record Contact(String number, String name) {}
    public record Msg(boolean mine, String from, String text) {}
    public record Conv(String id, String title, boolean group, String members, List<Msg> msgs) {}

    public static void encode(PhoneSnapshot s, FriendlyByteBuf b) {
        b.writeBoolean(s.open);
        b.writeUtf(s.focus);
        b.writeUtf(s.status);
        b.writeUtf(s.myNumber);
        b.writeVarInt(s.callState);
        b.writeUtf(s.callLabel);
        b.writeVarInt(s.contacts.size());
        for (Contact c : s.contacts) {
            b.writeUtf(c.number());
            b.writeUtf(c.name());
        }
        b.writeVarInt(s.convs.size());
        for (Conv c : s.convs) {
            b.writeUtf(c.id());
            b.writeUtf(c.title());
            b.writeBoolean(c.group());
            b.writeUtf(c.members());
            b.writeVarInt(c.msgs().size());
            for (Msg m : c.msgs()) {
                b.writeBoolean(m.mine());
                b.writeUtf(m.from());
                b.writeUtf(m.text());
            }
        }
    }

    public static PhoneSnapshot decode(FriendlyByteBuf b) {
        PhoneSnapshot s = new PhoneSnapshot();
        s.open = b.readBoolean();
        s.focus = b.readUtf();
        s.status = b.readUtf();
        s.myNumber = b.readUtf();
        s.callState = b.readVarInt();
        s.callLabel = b.readUtf();
        int nc = b.readVarInt();
        for (int i = 0; i < nc; i++) s.contacts.add(new Contact(b.readUtf(), b.readUtf()));
        int nv = b.readVarInt();
        for (int i = 0; i < nv; i++) {
            String id = b.readUtf();
            String title = b.readUtf();
            boolean group = b.readBoolean();
            String members = b.readUtf();
            int nm = b.readVarInt();
            List<Msg> msgs = new ArrayList<>();
            for (int j = 0; j < nm; j++) msgs.add(new Msg(b.readBoolean(), b.readUtf(), b.readUtf()));
            s.convs.add(new Conv(id, title, group, members, msgs));
        }
        return s;
    }

    public static void handle(PhoneSnapshot s, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onSnapshot(s)));
        c.setPacketHandled(true);
    }
}
