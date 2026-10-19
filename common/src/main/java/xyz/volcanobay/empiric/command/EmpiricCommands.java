package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.data.DataPrinter;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;

public class EmpiricCommands {

    public static final SuggestionProvider<CommandSourceStack> MODULE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(EmpiricCore.modules().types().stream().map(ModuleType::id), builder);

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("empiric").requires(source -> source.hasPermission(2));
        root.then(ModuleCommands.build());
        root.then(dump());
        root.then(TemperatureCommands.build());
        root.then(NetworkCommands.build());
        root.then(AtmosphereCommands.build());
        root.then(ChemistryCommands.build());
        root.then(DataCommands.build());
        root.then(OctCommands.build());
        root.then(DropCommands.build());
        root.then(UiCommands.build());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dump() {
        return Commands.literal("dump")
                .then(Commands.literal("server").executes(context -> dump(context, ServerAttachment.of(Side.SERVER), "server")))
                .then(Commands.literal("level").executes(context -> dump(context, LevelAttachment.of(context.getSource().getLevel()), "level")))
                .then(Commands.literal("entity").then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> dump(context, EntityAttachment.of(EntityArgument.getEntity(context, "target")), "entity"))))
                .then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(EmpiricCommands::dumpBlock)))
                .then(Commands.literal("chunk")
                        .executes(context -> dumpChunk(context, BlockPos.containing(context.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> dumpChunk(context, BlockPosArgument.getLoadedBlockPos(context, "pos")))));
    }

    public static boolean ensureEnabled(CommandSourceStack source, ModuleType<?, ?> type) {
        if (type.isEnabled()) {
            return false;
        }
        DebugSubscriptionSystem.subscribe(type);
        source.sendSuccess(() -> Component.literal("Subscribed " + DebugSubscriptionSystem.SUBSCRIBER + " to " + type.id())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Undo with /empiric modules " + type.id() + " unsubscribe")))), true);
        return true;
    }

    public static MutableComponent hover(String text, String tooltip, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tooltip))));
    }

    public static MutableComponent label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    public static void send(CommandContext<CommandSourceStack> context, Component component) {
        context.getSource().sendSuccess(() -> component, false);
    }

    public static String describe(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String print(Module<?> module) {
        return DataPrinter.print(module, (Spec) module.type().spec());
    }

    public static String print(Object value) {
        return DataPrinter.print(value, EmpiricCore.specs());
    }

    private static int dumpChunk(CommandContext<CommandSourceStack> context, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk chunk = context.getSource().getLevel().getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            context.getSource().sendFailure(Component.literal("Chunk " + chunkPos + " isn't loaded"));
            return 0;
        }
        return dump(context, ChunkAttachment.of(chunk), "chunk " + chunkPos.x + " " + chunkPos.z);
    }

    private static int dumpBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int count = 0;
        BlockAttachment block = BlockAttachment.find(context.getSource().getLevel(), pos);
        if (block != null) {
            count += dump(context, block, "block");
        }
        BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
        if (blockEntity != null) {
            count += dump(context, BlockEntityAttachment.of(blockEntity), "block entity");
        }
        if (block == null && blockEntity == null) {
            context.getSource().sendFailure(Component.literal("No modules there"));
        }
        return count;
    }

    public static int dump(CommandContext<CommandSourceStack> context, Attachment attachment, String name) {
        MutableComponent line = label(name + ": ");
        int[] count = {0};
        attachment.modules().forEach(module -> {
            line.append(hover(module.type().id() + " ", print(module), ChatFormatting.AQUA));
            count[0]++;
        });
        for (String dormant : attachment.modules().dormant().keySet()) {
            line.append(hover(dormant + " ", "dormant, " + attachment.modules().dormant().get(dormant).length + " bytes", ChatFormatting.DARK_GRAY));
        }
        if (count[0] == 0 && attachment.modules().dormant().isEmpty()) {
            line.append(Component.literal("no modules").withStyle(ChatFormatting.DARK_GRAY));
        }
        send(context, line);
        return count[0];
    }
}
