package xyz.volcanobay.empiric.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.attachment.OctModule;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.oct.FullOctBlock;

import java.util.ArrayList;
import java.util.List;

public final class OctModuleSystem {

    public static final SpatialModuleStore<OctAttachment> STORE = new SpatialModuleStore<>("empiric:octs",
            (level, entry) -> new OctAttachment(level, new OctPos(BlockPos.of(entry.getLong("pos")), entry.getByte("octant"))),
            (attachment, entry) -> {
                entry.putLong("pos", attachment.pos().pos().asLong());
                entry.putByte("octant", attachment.pos().octant());
            });

    private static List<ModuleType<?, ?>> autoTypes;

    private OctModuleSystem() {
    }

    public static void bootstrap() {
    }

    public static OctAttachment find(Level level, OctPos pos) {
        return STORE.find(level, pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    public static OctAttachment get(Level level, OctPos pos) {
        OctAttachment attachment = find(level, pos);
        if (attachment != null) {
            return attachment;
        }
        return STORE.track(new OctAttachment(level, pos));
    }

    public static void prune(OctAttachment attachment) {
        STORE.prune(attachment);
    }

    private static boolean rulesActive() {
        if (autoTypes == null) {
            List<ModuleType<?, ?>> types = new ArrayList<>();
            for (ModuleType<?, ?> type : EmpiricCore.modules().types()) {
                if (type.attachmentType() == EmpiricAttachments.OCT && type.autoAttaches()) {
                    types.add(type);
                }
            }
            autoTypes = List.copyOf(types);
        }
        for (ModuleType<?, ?> type : autoTypes) {
            if (type.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void octChanged(Level level, OctPos pos, BlockState oldState, BlockState newState) {
        ModuleSystem modules = EmpiricCore.modules();
        OctAttachment attachment = find(level, pos);
        boolean sameBlock = oldState.is(newState.getBlock());
        if (attachment != null) {
            attachment.modules().forEach(module -> {
                boolean survives = module instanceof OctModule octModule ? octModule.survives(oldState, newState) : sameBlock;
                if (!survives) {
                    modules.detach((OctAttachment) module.attachment(), (ModuleType) module.type());
                } else if (module instanceof OctModule octModule) {
                    octModule.onStateChanged(oldState, newState);
                }
            });
            if (!sameBlock) {
                attachment.modules().clearDormant();
            }
        }
        if (!sameBlock && !newState.isAir() && rulesActive()) {
            OctAttachment candidate = attachment != null ? attachment : get(level, pos);
            modules.applyRules(candidate);
            attachment = candidate;
        }
        if (attachment != null) {
            prune(attachment);
        }
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!(oldState.getBlock() instanceof FullOctBlock) || newState.getBlock() instanceof FullOctBlock || STORE.isEmpty(level)) {
            return;
        }
        ModuleSystem modules = EmpiricCore.modules();
        for (int octant = 0; octant < 8; octant++) {
            OctAttachment attachment = find(level, new OctPos(pos, octant));
            if (attachment != null) {
                modules.detachAll(attachment);
                attachment.modules().clearDormant();
                prune(attachment);
            }
        }
    }
}
