package xyz.volcanobay.empiric.oct;

import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.api.oct.OctBlock;

import java.util.Arrays;

public class FullOctVoxelShape {
    
    private static final FullOctVoxelShape OCT;
    
    private final OctVoxelShape[] shapes;
    private VoxelShape delegated;
    
    public FullOctVoxelShape() {
        this.shapes = new OctVoxelShape[8];
    }
    
    public OctVoxelShape get(byte octant) {
        if (delegated == null) {
            return OCT.get(octant);
        }
        
        OctVoxelShape shape = shapes[octant];
        if (shape == null) {
            shape = new OctVoxelShape(delegated, octant);
            shapes[octant] = shape;
        }
        return shape;
    }
    
    public FullOctVoxelShape setShape(VoxelShape delegated) {
        if (this.delegated != delegated) {
            Arrays.fill(this.shapes, null);
        }
        this.delegated = delegated;
        return this;
    }
    
    static {
        OCT = new FullOctVoxelShape();
        OCT.setShape(OctBlock.BOX);
    }
}
