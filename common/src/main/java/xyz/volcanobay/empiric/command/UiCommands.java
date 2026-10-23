package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.ui.UiLayouts;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.UiScreens;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.networking.packet.UiEditorS2CPacket;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.system.UiDemo;
import xyz.volcanobay.empiric.system.UiSystem;

import java.util.List;
import java.util.Map;

public final class UiCommands {

    private static final DynamicCommandExceptionType UNKNOWN_SCREEN = new DynamicCommandExceptionType(id -> Component.literal("No screen " + id));
    private static final SuggestionProvider<CommandSourceStack> SCREENS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(UiScreens.all().stream().map(UiScreenType::id), builder);

    private static final SuggestionProvider<CommandSourceStack> LAYOUTS = (context, builder) ->
            SharedSuggestionProvider.suggest(UiLayouts.loaded().keySet(), builder);

    private UiCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("ui")
                .executes(UiCommands::list)
                .then(Commands.literal("demo").executes(context -> demo(context, UiDemo.SCREEN))
                        .then(Commands.literal("layout").executes(context -> demo(context, UiDemo.LAYOUT_SCREEN)))
                        .then(Commands.literal("inscriber").executes(context -> demo(context, UiDemo.INSCRIBER_SCREEN)))
                        .then(Commands.literal("filter").executes(context -> demo(context, UiDemo.FILTER_SCREEN))))
                .then(Commands.literal("surface").executes(context -> surface(context, 2)))
                .then(Commands.literal("tooltip").executes(context -> tooltip(context, true))
                        .then(Commands.literal("remove").executes(context -> tooltip(context, false))))
                .then(Commands.literal("ghost").executes(UiCommands::ghost))
                .then(Commands.literal("editor").executes(context -> editor(context, ""))
                        .then(Commands.argument("layout", ResourceLocationArgument.id()).suggests(LAYOUTS)
                                .executes(context -> editor(context, ResourceLocationArgument.getId(context, "layout").toString()))))
                .then(Commands.literal("close").executes(UiCommands::close))
                .then(Commands.literal("open").then(Commands.argument("screen", ResourceLocationArgument.id()).suggests(SCREENS)
                        .executes(context -> open(context, null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> open(context, BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MutableComponent screens = EmpiricCommands.label("Screens: ");
        for (UiScreenType<?> screen : UiScreens.all()) {
            screens.append(EmpiricCommands.hover(screen.id() + " ", "on " + screen.moduleType().id(), ChatFormatting.AQUA));
        }
        EmpiricCommands.send(context, screens);
        Map<Module<?>, List<UiSystem.Viewer>> open = UiSystem.openScreens();
        if (open.isEmpty()) {
            EmpiricCommands.send(context, EmpiricCommands.label("No screens are open"));
        }
        for (Map.Entry<Module<?>, List<UiSystem.Viewer>> entry : open.entrySet()) {
            MutableComponent line = EmpiricCommands.hover(entry.getKey().type().id(), EmpiricCommands.print(entry.getKey()), ChatFormatting.AQUA)
                    .append(EmpiricCommands.label(" viewed by "));
            for (UiSystem.Viewer viewer : entry.getValue()) {
                line.append(Component.literal(viewer.player().getGameProfile().getName() + " ").withStyle(ChatFormatting.WHITE))
                        .append(EmpiricCommands.label("(" + viewer.screen().id() + ") "));
            }
            EmpiricCommands.send(context, line);
        }
        return open.size();
    }

    private static int demo(CommandContext<CommandSourceStack> context, UiScreenType<UiDemo.State> screen) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EmpiricCommands.ensureEnabled(context.getSource(), UiDemo.TYPE);
        UiDemo.State state = UiDemo.TYPE.get(context.getSource().getServer());
        if (state == null) {
            state = UiDemo.TYPE.attach(context.getSource().getServer());
        }
        if (!screen.open(player, state)) {
            context.getSource().sendFailure(Component.literal("The demo screen refused to open"));
            return 0;
        }
        return 1;
    }

    /**
     * Hangs the demo screen in front of the player, to try pointing at widgets in the world.
     */
    private static int surface(CommandContext<CommandSourceStack> context, float width) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EmpiricCommands.ensureEnabled(context.getSource(), UiDemo.TYPE);
        UiDemo.State state = UiDemo.TYPE.get(context.getSource().getServer());
        if (state == null) {
            state = UiDemo.TYPE.attach(context.getSource().getServer());
        }
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(3));
        if (!UiSystem.showSurface(player, UiDemo.SCREEN, state, at.x, at.y, at.z, player.getYRot(), width)) {
            context.getSource().sendFailure(Component.literal("The demo screen refused to show"));
            return 0;
        }
        return 1;
    }

    /**
     * Opens the layout editor on the player's client, on a layout or the list of layouts. A layout that isn't loaded
     * starts as a new one with that id.
     */
    private static int editor(CommandContext<CommandSourceStack> context, String layout) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ModuleSyncSystem.send(PacketTarget.player(player), new UiEditorS2CPacket(layout));
        EmpiricCommands.send(context, EmpiricCommands.label(layout.isEmpty() ? "Opening the layout editor" : "Opening " + layout + " in the layout editor"));
        return 1;
    }

    /**
     * Circles a fake viewer's pointer over the demo screen for 20 seconds, wherever the player has it open, to check
     * presence cursors without a second client.
     */
    private static int ghost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UiDemo.State state = UiDemo.TYPE.get(context.getSource().getServer());
        if (state == null) {
            context.getSource().sendFailure(Component.literal("Open the demo first, with /empiric ui demo or surface"));
            return 0;
        }
        UiSystem.ghost(player, UiDemo.SCREEN, state, 20);
        EmpiricCommands.send(context, EmpiricCommands.label("A ghost pointer circles the demo screen for 20 seconds"));
        return 1;
    }

    /**
     * Attaches the demo gauge to the block the player looks at, or removes it, to try floating tooltips.
     */
    private static int tooltip(CommandContext<CommandSourceStack> context, boolean attach) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EmpiricCommands.ensureEnabled(context.getSource(), UiDemo.GAUGE);
        HitResult hit = player.pick(player.blockInteractionRange(), 0, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.literal("Look at a block first"));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!attach) {
            boolean removed = UiDemo.GAUGE.detach(player.level(), pos);
            EmpiricCommands.send(context, EmpiricCommands.label(removed ? "Removed the gauge" : "No gauge there"));
            return removed ? 1 : 0;
        }
        if (UiDemo.GAUGE.get(player.level(), pos) == null) {
            UiDemo.GAUGE.attach(player.level(), pos);
        }
        EmpiricCommands.send(context, EmpiricCommands.label("Attached a gauge at " + pos.toShortString()));
        return 1;
    }

    private static int close(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UiSystem.close(context.getSource().getPlayerOrException());
        return 1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int open(CommandContext<CommandSourceStack> context, BlockPos pos) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UiScreenType<?> screen = UiScreens.get(ResourceLocationArgument.getId(context, "screen"));
        if (screen == null) {
            throw UNKNOWN_SCREEN.create(ResourceLocationArgument.getId(context, "screen"));
        }
        ModuleType type = screen.moduleType();
        EmpiricCommands.ensureEnabled(context.getSource(), type);
        Attachment attachment = attachment(type.attachmentType(), player, pos == null ? player.blockPosition() : pos);
        Module<?> module = attachment == null ? null : (Module<?>) type.get(attachment);
        if (module == null) {
            context.getSource().sendFailure(Component.literal("No " + type.id() + " module there"));
            return 0;
        }
        if (!UiSystem.openTarget(player, new UiSystem.Target(screen, module))) {
            context.getSource().sendFailure(Component.literal(screen.id() + " refused to open for " + player.getGameProfile().getName()));
            return 0;
        }
        return 1;
    }

    private static Attachment attachment(AttachmentType<?, ?> type, ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (type == EmpiricAttachments.SERVER) {
            return ServerAttachment.of(Side.SERVER);
        }
        if (type == EmpiricAttachments.LEVEL) {
            return LevelAttachment.of(level);
        }
        if (type == EmpiricAttachments.ENTITY) {
            return EntityAttachment.of(player);
        }
        if (type == EmpiricAttachments.CHUNK) {
            return ChunkAttachment.of(level.getChunkAt(pos));
        }
        if (type == EmpiricAttachments.BLOCK) {
            return BlockAttachment.find(level, pos);
        }
        if (type == EmpiricAttachments.BLOCK_ENTITY) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity == null ? null : BlockEntityAttachment.of(blockEntity);
        }
        return null;
    }
}
