package dev.phonemod;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PhoneConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue RING_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PHONE_ITEM;
    public static final ForgeConfigSpec.BooleanValue HANGUP_IF_PHONE_LOST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RING_TIMEOUT_SECONDS = b.comment("Seconds a call rings before it is dropped.")
                .defineInRange("ringTimeoutSeconds", 30, 5, 300);
        REQUIRE_PHONE_ITEM = b.comment("A phone item must be in the inventory to call, receive calls and open the phone.")
                .define("requirePhoneItem", true);
        HANGUP_IF_PHONE_LOST = b.comment("Hang up a call when one of the players no longer has a phone item.")
                .define("hangUpIfPhoneLost", true);
        SPEC = b.build();
    }

    private PhoneConfig() {}
}
