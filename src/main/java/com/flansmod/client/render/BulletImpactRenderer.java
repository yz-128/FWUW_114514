package com.flansmod.client.render;

import com.flansmod.client.model.ImpactMark;
import com.flansmod.common.FlansMod;
import com.flansmod.common.entity.BulletImpacts;
import com.flansmod.common.network.BlockImpactPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** World-space, depth-tested marks; client cosmetics exist only after a server hit message. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class BulletImpactRenderer extends RenderStateShard {
    private BulletImpactRenderer(){super("flansmod_bullet_impact",()->{},()->{});}
    private static final RenderType TYPE=RenderType.create("flansmod_bullet_impact",DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,32768,false,true,RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private static final class Mark {
        final BlockImpactPayload hit;final List<ImpactMark.Vertex> mesh;int age;
        Mark(BlockImpactPayload hit,List<ImpactMark.Vertex> mesh){this.hit=hit;this.mesh=mesh;}
    }
    private static final Deque<Mark> MARKS=new ArrayDeque<>();
    private static ClientLevel world;
    private static void bind(ClientLevel level){if(world!=level){MARKS.clear();world=level;}}
    public static void accept(BlockImpactPayload hit) {
        var mc=Minecraft.getInstance();bind(mc.level);
        if(world==null||!hit.valid()||!world.hasChunkAt(hit.block())||mc.player==null
                ||mc.player.distanceToSqr(hit.point())>(BulletImpacts.RANGE+8)*(BulletImpacts.RANGE+8))return;
        var state=Block.stateById(hit.state());if(state.isAir())return;
        var random=new Random(hit.seed());var point=hit.point().add(hit.face().getStepX()*.015,hit.face().getStepY()*.015,hit.face().getStepZ()*.015);
        for(int i=0;i<5;i++)world.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),point.x,point.y,point.z,
                hit.face().getStepX()*.035+(random.nextDouble()-.5)*.035,
                hit.face().getStepY()*.035+random.nextDouble()*.035,
                hit.face().getStepZ()*.035+(random.nextDouble()-.5)*.035);
        if(Block.getId(world.getBlockState(hit.block()))!=hit.state())return;
        var mesh=ImpactMark.mesh(hit,state.getCollisionShape(world,hit.block()).toAabbs());
        if(mesh.isEmpty())return;if(MARKS.size()>=ImpactMark.MAX_MARKS)MARKS.removeFirst();MARKS.addLast(new Mark(hit,mesh));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();bind(mc.level);if(world==null||mc.isPaused())return;
        MARKS.removeIf(mark->++mark.age>=ImpactMark.LIFETIME||!world.hasChunkAt(mark.hit.block())
                ||Block.getId(world.getBlockState(mark.hit.block()))!=mark.hit.state());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){MARKS.clear();world=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();bind(mc.level);if(world==null||MARKS.isEmpty())return;
        var camera=event.getCamera().getPosition();var pose=new PoseStack();
        pose.mulPose(FmumWorldEffectsPose.worldStage(event.getModelViewMatrix(),RenderSystem.getModelViewMatrix()));
        var buffer=mc.renderBuffers().bufferSource();var output=buffer.getBuffer(TYPE);
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);
        for(var mark:MARKS) {
            var hit=mark.hit;if(camera.distanceToSqr(hit.point())>BulletImpacts.RANGE*BulletImpacts.RANGE)continue;
            if(!world.hasChunkAt(hit.block())||Block.getId(world.getBlockState(hit.block()))!=hit.state())continue;
            var lightPos=hit.block().relative(hit.face());
            float brightness=Math.max(.15F,Math.max(world.getBrightness(LightLayer.BLOCK,lightPos),
                    world.getBrightness(LightLayer.SKY,lightPos)-world.getSkyDarken())/15F)*world.getShade(hit.face(),true);
            float alpha=ImpactMark.alpha(mark.age,partial);pose.pushPose();
            pose.translate(hit.block().getX()-camera.x,hit.block().getY()-camera.y,hit.block().getZ()-camera.z);
            for(var p:mark.mesh){int shade=Math.clamp((int)(p.shade()*brightness),0,255);
                output.addVertex(pose.last(),(float)p.x(),(float)p.y(),(float)p.z()).setColor(shade,shade,shade,(int)(255*p.opacity()*alpha));}
            pose.popPose();
        }
        buffer.endBatch(TYPE);
    }
}
