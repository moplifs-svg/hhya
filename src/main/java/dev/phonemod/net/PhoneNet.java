package dev.phonemod.net;

import dev.phonemod.PhoneMod;
import dev.phonemod.PhoneService;
import dev.phonemod.call.CallManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PhoneNet {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PhoneMod.ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    // client -> server
    public record Open() {}
    public record AddContact(String name, String number) {}
    public record Dial(String number) {}
    public record CallAction(int action) {}          // 0 answer, 1 decline, 2 hang up
    public record SendMsg(String conv, String text) {}
    public record StartChat(String number) {}
    public record CreateGroup(String name, String numbers) {}
    public record AddMember(String conv, String number) {}

    private static void run(Supplier<NetworkEvent.Context> ctx, Consumer<ServerPlayer> action) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer p = c.getSender();
            if (p != null) action.accept(p);
        });
        c.setPacketHandled(true);
    }

    public static void register() {
        int id = 0;
        CH.registerMessage(id++, PhoneSnapshot.class, PhoneSnapshot::encode, PhoneSnapshot::decode, PhoneSnapshot::handle);

        CH.registerMessage(id++, Open.class, (m, b) -> {}, b -> new Open(),
                (m, c) -> run(c, PhoneService::open));

        CH.registerMessage(id++, AddContact.class,
                (m, b) -> { b.writeUtf(m.name()); b.writeUtf(m.number()); },
                b -> new AddContact(b.readUtf(100), b.readUtf(100)),
                (m, c) -> run(c, p -> PhoneService.addContact(p, m.name(), m.number())));

        CH.registerMessage(id++, Dial.class,
                (m, b) -> b.writeUtf(m.number()),
                b -> new Dial(b.readUtf(100)),
                (m, c) -> run(c, p -> CallManager.dial(p, m.number())));

        CH.registerMessage(id++, CallAction.class,
                (m, b) -> b.writeVarInt(m.action()),
                b -> new CallAction(b.readVarInt()),
                (m, c) -> run(c, p -> {
                    switch (m.action()) {
                        case 0 -> CallManager.answer(p);
                        case 1 -> CallManager.decline(p);
                        default -> CallManager.hangup(p);
                    }
                }));

        CH.registerMessage(id++, SendMsg.class,
                (m, b) -> { b.writeUtf(m.conv()); b.writeUtf(m.text()); },
                b -> new SendMsg(b.readUtf(100), b.readUtf(1000)),
                (m, c) -> run(c, p -> PhoneService.sendMessage(p, m.conv(), m.text())));

        CH.registerMessage(id++, StartChat.class,
                (m, b) -> b.writeUtf(m.number()),
                b -> new StartChat(b.readUtf(100)),
                (m, c) -> run(c, p -> PhoneService.startChat(p, m.number())));

        CH.registerMessage(id++, CreateGroup.class,
                (m, b) -> { b.writeUtf(m.name()); b.writeUtf(m.numbers()); },
                b -> new CreateGroup(b.readUtf(100), b.readUtf(500)),
                (m, c) -> run(c, p -> PhoneService.createGroup(p, m.name(), m.numbers())));

        CH.registerMessage(id++, AddMember.class,
                (m, b) -> { b.writeUtf(m.conv()); b.writeUtf(m.number()); },
                b -> new AddMember(b.readUtf(100), b.readUtf(100)),
                (m, c) -> run(c, p -> PhoneService.addMember(p, m.conv(), m.number())));
    }

    private PhoneNet() {}
}
