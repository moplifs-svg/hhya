package dev.phonemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.phonemod.call.CallManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class PhoneCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("phone")
                .then(Commands.literal("call")
                        .then(Commands.argument("number", StringArgumentType.word())
                                .executes(c -> {
                                    CallManager.dial(c.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(c, "number"));
                                    return 1;
                                })))
                .then(Commands.literal("answer").executes(c -> {
                    CallManager.answer(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("decline").executes(c -> {
                    CallManager.decline(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("hangup").executes(c -> {
                    CallManager.hangup(c.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private PhoneCommands() {}
}
