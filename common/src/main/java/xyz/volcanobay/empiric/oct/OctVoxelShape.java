package xyz.volcanobay.empiric.oct;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.mixin.oct.VoxelShapeAccessor;

import java.util.EnumMap;
import java.util.List;

public class OctVoxelShape extends VoxelShape {
    
    private final VoxelShape delegated;
    
    private final byte octant;
    private final double oX;
    private final double oY;
    private final double oZ;
    
    private EnumMap<Direction.Axis, DoubleList> coords;
    private AABB bounds;
    
    public OctVoxelShape(VoxelShape delegated, byte octant) {
        super(((VoxelShapeAccessor)delegated).getShape());
        this.delegated = delegated;
        this.octant = octant;
        
        this.oX = ((octant & 1) != 0) ? 0.5 : 0;
        this.oY = ((octant & 2) != 0) ? 0.5 : 0;
        this.oZ = ((octant & 4) != 0) ? 0.5 : 0;
        
        this.coords = null;
    }
    
    @Override
    public double min(Direction.Axis axis) {
        return this.delegated.min(axis) + axis.choose(oX, oY, oZ);
    }
    
    @Override
    public double max(Direction.Axis axis) {
        return this.delegated.max(axis) + axis.choose(oX, oY, oZ);
    }
    
    @Override
    public AABB bounds() {
        if (this.bounds == null) {
            this.bounds = this.delegated.bounds().move(this.oX, this.oY, this.oZ);
        }
        return bounds;
    }
    
    @Override
    public VoxelShape singleEncompassing() {
        return super.singleEncompassing();
    }
    
    @Override
    protected double get(Direction.Axis axis, int index) {
        return super.get(axis, index) + axis.choose(oX, oY, oZ);
    }
    
    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        if (this.coords == null) {
            this.coords = new EnumMap<>(Direction.Axis.class);
            Direction.Axis[] axes = Direction.Axis.values();
            for (int i = 0; i < axes.length; i++) {
                Direction.Axis a = axes[i];
                DoubleList delegatedCoords = new DoubleArrayList(this.delegated.getCoords(a));
                delegatedCoords.replaceAll(d -> d + axis.choose(oX, oY, oZ));
                this.coords.put(a, delegatedCoords);
            }
            
        }
        return delegated.getCoords(axis);
    }
    
    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }
    
    @Override
    public VoxelShape move(double xOffset, double yOffset, double zOffset) {
        return new OctVoxelShape(super.move(xOffset, yOffset, zOffset), this.octant);
    }
    
    @Override
    public VoxelShape optimize() {
        return new OctVoxelShape(super.optimize(), this.octant);
    }
    
    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer action) {
        super.forAllEdges(action);
    }
    
    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer action) {
        super.forAllBoxes(action);
    }
    
    @Override
    public List<AABB> toAabbs() {
        return super.toAabbs().stream().map(aabb -> aabb.move(oX, oY, oZ)).toList();
    }
    
    @Override
    public double min(Direction.Axis axis, double primaryPosition, double secondaryPosition) {
        return super.min(axis, primaryPosition, secondaryPosition);
    }
}
