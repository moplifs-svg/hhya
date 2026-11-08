package dev.phonemod.client;

import dev.phonemod.net.PhoneSnapshot;
import net.minecraft.client.Minecraft;

/** Client side state shared by the screen and the key / ring handlers. */
public final class ClientHooks {
    public static PhoneSnapshot snap = new PhoneSnapshot();
    public static String status = "";

    public static void onSnapshot(PhoneSnapshot s) {
        snap = s;
        status = s.status;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhoneScreen screen) {
            screen.refresh();
        } else if (s.open && mc.player != null && mc.screen == null) {
            mc.setScreen(new PhoneScreen());
        }
    }

    public static void reset() {
        snap = new PhoneSnapshot();
        status = "";
    }

    private ClientHooks() {}
}
