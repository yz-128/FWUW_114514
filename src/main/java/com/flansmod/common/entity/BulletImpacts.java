package com.flansmod.common.entity;

import com.flansmod.common.network.BlockImpactPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Server authority and dimension-scoped, distance-limited cosmetic notification. */
public final class BulletImpacts {
    private BulletImpacts() { }
    public static final double RANGE=64D;
    /** Entry faces only, bounded by the path actually flown before a target or hard wall. */
    public static List<BlockHitResult> passedBlocks(BlockGetter world,Vec3 from,Vec3 to,Entity projectile) {
        if(from.distanceToSqr(to)<1E-20)return List.of();
        var result=new ArrayList<BlockHitResult>();
        var context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,projectile);
        BlockGetter.traverseBlocks(from,to,context,(clip,pos)->{
            var state=world.getBlockState(pos);
            if(state.is(BulletBlockTrace.PASS_THROUGH)) {
                var hit=clip.getBlockShape(state,world,pos).clip(from,to,pos);
                if(hit!=null&&!hit.isInside())result.add(new BlockHitResult(hit.getLocation(),hit.getDirection(),pos.immutable(),false));
            }
            return result.size()>=16?Boolean.TRUE:null;
        },clip->null);
        return List.copyOf(result);
    }
    public static void send(ServerLevel level,BlockHitResult hit) {
        var state=level.getBlockState(hit.getBlockPos());
        if(state.isAir())return;
        var point=hit.getLocation();
        PacketDistributor.sendToPlayersNear(level,null,point.x,point.y,point.z,RANGE,
                new BlockImpactPayload(hit.getBlockPos(),point,hit.getDirection(),Block.getId(state),level.random.nextLong()));
    }
}
