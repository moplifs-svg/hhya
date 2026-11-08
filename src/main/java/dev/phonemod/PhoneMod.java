package dev.phonemod;

import dev.phonemod.client.ClientSetup;
import dev.phonemod.net.PhoneNet;
import dev.phonemod.voice.PhoneVoiceAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import su.plo.voice.api.server.PlasmoVoiceServer;

@Mod(PhoneMod.ID)
public class PhoneMod {
    public static final String ID = "phonemod";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final RegistryObject<Item> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("phone",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.phonemod"))
                    .icon(() -> new ItemStack(PHONE.get()))
                    .displayItems((params, out) -> out.accept(PHONE.get()))
                    .build());

    public PhoneMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TABS.register(bus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PhoneConfig.SPEC);
        PhoneNet.register();
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(bus));

        try {
            PlasmoVoiceServer.getAddonsLoader().load(PhoneVoiceAddon.INSTANCE);
        } catch (Throwable t) {
            LOGGER.error("Could not load the Plasmo Voice addon, calls will have no voice", t);
        }
    }
}
