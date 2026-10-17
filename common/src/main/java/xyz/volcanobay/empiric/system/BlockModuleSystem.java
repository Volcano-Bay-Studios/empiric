package xyz.volcanobay.empiric.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockModule;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;

import java.util.ArrayList;
import java.util.List;

public final class BlockModuleSystem {

    public static final SpatialModuleStore<BlockAttachment> STORE = new SpatialModuleStore<>("empiric:blocks",
            (level, entry) -> new BlockAttachment(level, BlockPos.of(entry.getLong("pos"))),
            (attachment, entry) -> entry.putLong("pos", attachment.pos().asLong()));

    private static List<ModuleType<?, ?>> autoTypes;

    private BlockModuleSystem() {
    }

    public static void bootstrap() {
    }

    public static BlockAttachment find(Level level, BlockPos pos) {
        return STORE.find(level, pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockAttachment get(Level level, BlockPos pos) {
        BlockAttachment attachment = find(level, pos);
        if (attachment != null) {
            return attachment;
        }
        return STORE.track(new BlockAttachment(level, pos));
    }

    public static void prune(BlockAttachment attachment) {
        STORE.prune(attachment);
    }

    private static List<ModuleType<?, ?>> autoTypes() {
        if (autoTypes == null) {
            List<ModuleType<?, ?>> types = new ArrayList<>();
            for (ModuleType<?, ?> type : EmpiricCore.modules().types()) {
                if (type.attachmentType() == EmpiricAttachments.BLOCK && type.autoAttaches()) {
                    types.add(type);
                }
            }
            autoTypes = List.copyOf(types);
        }
        return autoTypes;
    }

    private static boolean rulesActive() {
        for (ModuleType<?, ?> type : autoTypes()) {
            if (type.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void blockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        ModuleSystem modules = EmpiricCore.modules();
        BlockAttachment attachment = find(level, pos);
        boolean sameBlock = oldState.is(newState.getBlock());
        if (attachment != null) {
            attachment.modules().forEach(module -> {
                boolean survives = module instanceof BlockModule blockModule ? blockModule.survives(oldState, newState) : sameBlock;
                if (!survives) {
                    modules.detach((BlockAttachment) module.attachment(), (ModuleType) module.type());
                } else if (module instanceof BlockModule blockModule) {
                    blockModule.onStateChanged(oldState, newState);
                }
            });
            if (!sameBlock) {
                attachment.modules().clearDormant();
            }
        }
        if (!sameBlock && !newState.isAir() && rulesActive()) {
            BlockAttachment candidate = attachment != null ? attachment : get(level, pos);
            modules.applyRules(candidate);
            attachment = candidate;
        }
        if (attachment != null) {
            prune(attachment);
        }
    }
}
