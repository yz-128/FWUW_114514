package com.flansmod.client.render;

import com.flansmod.client.GunAnimationRegistry;
import com.flansmod.client.model.FmumSmoke;
import com.flansmod.client.model.FmumCasing;
import com.flansmod.client.model.GunAnimations;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.ModelGeometry.Model;
import com.flansmod.common.FlansMod;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/** Original pack meshes/textures; flash follows the gun, smoke and ejected rounds occupy world space. */
@EventBusSubscriber(modid=FlansMod.MODID,value=Dist.CLIENT)
public final class MuzzleEffectsRenderer {
	private MuzzleEffectsRenderer() { }
	private record WorldSmoke(FmumSmoke effect,net.minecraft.world.phys.Vec3 position) { }
	private static final Deque<WorldSmoke> SMOKE=new ArrayDeque<>();
	private record WorldCase(FmumCasing effect,net.minecraft.world.phys.Vec3 origin) { }
	private static final Deque<WorldCase> CASES=new ArrayDeque<>();
	private static LivingEntity renderingEntity;
	private static int localSlot=-1;
	private static GunType localGun;

	public static void bindLocal(GunType gun,int slot) {
		if(gun!=localGun || slot!=localSlot)GunAnimationRegistry.localPlayer(false).effects.reset();
		localGun=gun;localSlot=slot;
	}
	public static void shoot(LivingEntity entity,boolean offHand,GunType gun,Model model,boolean smoke,long seed) {
		var stack=offHand?entity.getOffhandItem():entity.getMainHandItem();
		var flash=FmumMuzzlePose.flash(com.flansmod.common.GunAttachments.view(gun,stack),model);
		int count=flash==null?1:flash.textures();
		var random=new java.util.Random(seed);
		GunAnimationRegistry.get(entity,offHand).effects.shoot(gun,flash,random.nextInt(count),random.nextFloat()*360F,smoke);
	}
	public static void remote(com.flansmod.common.network.GunShotPayload shot) {
		var mc=Minecraft.getInstance();
		if(mc.level==null)return;
		if(!(mc.level.getEntity(shot.entity()) instanceof LivingEntity entity))return;
		var stack=shot.offHand()?entity.getOffhandItem():entity.getMainHandItem();
		if(!(InfoType.getType(stack) instanceof GunType gun) || !gun.shortName.equals(shot.gun()) || gun.itsType==GunType.GunItsType.MAG)return;
		var rig=ModelCache.rigFor(gun,gun.modelScale);
		shoot(entity,shot.offHand(),gun,rig==null?null:rig.animation,entity==mc.player,shot.seed());
	}
	public static void ejection(com.flansmod.common.network.GunEjectionPayload packet) {
		var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null)return;
		if(!(InfoType.getType(mc.player.getMainHandItem()) instanceof GunType gun) || !packet.gun().equals(gun.shortName))return;
		var round=com.flansmod.common.types.BulletType.getBullet(packet.round());
		if(round!=null)GunAnimationRegistry.localPlayer(false).effects.requestCase(gun,round,packet.manual(),packet.seed());
	}
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public static void beforeEntity(RenderLivingEvent.Pre<?,?> event) {
		if(!event.isCanceled())renderingEntity=event.getEntity();
	}
	@SubscribeEvent
	public static void afterEntity(RenderLivingEvent.Post<?,?> event) { renderingEntity=null; }

	public static void render(PoseStack pose,MultiBufferSource buffers,GunType gun,ItemStack stack,
			ItemDisplayContext context,Model model,int light,int overlay,boolean mirrored) {
		var mc=Minecraft.getInstance();
		boolean held=context.firstPerson() || context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		LivingEntity owner=context.firstPerson()?mc.player:renderingEntity;
		if(!held || owner==null || gun.itsType==GunType.GunItsType.MAG)return;
		// ItemInHandRenderer can retain a pre-NBT stack until its next tick. Resolve the logical
		// hand from the render context instead of losing shot frames to reference identity.
		boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		boolean offHand=left!=(owner.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT);
		if(InfoType.getType(offHand?owner.getOffhandItem():owner.getMainHandItem())!=gun.definition())return;
		GunAnimations animation=GunAnimationRegistry.get(owner,offHand);
		float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);
		var flash=FmumMuzzlePose.flash(gun,model);
		float alpha=animation.effects.gun==gun.definition()?animation.effects.alpha(partial):0;
		if(flash!=null && alpha>=1F/255F && flash.scale()>0F) {
			var texture=Textures.of(flash.texture(animation.effects.variant),false);
			var rig=ModelCache.rigForReference(flash.model(),1F,texture);
			if(texture!=null && rig!=null && !rig.quads.isEmpty()) {
				if(context.firstPerson() && ShaderMuzzleRenderer.shadersEnabled()) {
					if(!ShaderMuzzleRenderer.shadowPass())ShaderMuzzleRenderer.capture(offHand,FmumShaderFlash.capture(
							rig,texture,RenderSystem.getProjectionMatrix(),RenderSystem.getModelViewMatrix(),
							pose.last().pose(),flash,animation.effects.roll,alpha));
				} else {
					pose.pushPose();pose.mulPose(flash.pose(animation.effects.roll));
					RigRenderer.render(pose,buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)),rig,
							LightTexture.FULL_BRIGHT,overlay,1F,1F,1F,alpha,mirrored);
					pose.popPose();
					var glow=FmumMuzzleGlow.frame(pose.last().pose(),RenderSystem.getModelViewMatrix(),flash,alpha);
					if(glow!=null)FmumMuzzleGlow.emit(buffers.getBuffer(MuzzleGlowRenderer.TYPE),glow);
				}
			}
		}
		// A shot requests emission once; frame rate, ADS and the number of model parts never repeat it.
		int shots=animation.effects.takeSmoke(gun.definition());
		if(shots>0 && owner==mc.player) {
			var camera=mc.gameRenderer.getMainCamera();
			// Modern hand poses start with the inverse camera rotation; its shader model-view
			// restores it. Undo that pair before turning gun-local points into world positions.
			var world=FmumWorldEffectsPose.gunToCameraRelativeWorld(pose.last().pose(),camera.rotation(),RenderSystem.getModelViewMatrix());
			for(int i=0;i<shots;i++) {
				emit(gun.muzzleSmoke,new Matrix4f(world).mul(FmumMuzzlePose.smoke(gun,model,false)),owner,
						gun.muzzleSmoke.freeRotate!=0?owner.getRandom().nextFloat()*360F:0F);
				emit(gun.chamberSmoke,new Matrix4f(world).mul(FmumMuzzlePose.smoke(gun,model,true)),owner,LegacySightPose.forGun(gun).roll());
			}
		}
		if(owner==mc.player && !offHand) {
			var camera=mc.gameRenderer.getMainCamera();
			var world=FmumWorldEffectsPose.gunToCameraRelativeWorld(pose.last().pose(),camera.rotation(),RenderSystem.getModelViewMatrix());
			for(var request:animation.effects.takeCases(gun.definition())) {
				var origin=FmumCasing.origin(model,new Matrix4f(world));
				// Cases live four times longer than the packs author, so a high-rate weapon can
				// have a few hundred on the ground at once. The cap only has to stop unbounded
				// growth; the rig is tiny and a settled case costs no physics.
				if(CASES.size()>=256)CASES.removeFirst();
				CASES.addLast(new WorldCase(new FmumCasing(request.round(),model,world,request.manual(),request.seed(),LegacySightPose.forGun(gun).roll()),
						camera.getPosition().add(origin.x,origin.y,origin.z)));
			}
		}
		if(context.firstPerson() && !offHand) {
			if(!ShaderMuzzleRenderer.shadersEnabled())renderSmoke(buffers,light,overlay);
			// Casings are deliberately NOT drawn here. Vanilla clears the depth buffer before the
			// hand pass so the held item never clips into terrain; anything drawn in this pass is
			// therefore painted over the world, which is why a case used to show through trees and
			// why it picked up the hand's item light instead of the light at its own position.
			// They are drawn from the world stage instead, where depth and light are correct.
		}
	}
	private static void emit(GunType.Smoke spec,Matrix4f point,LivingEntity owner,float roll) {
		if(spec.vanishConst<=0 || spec.model==null || spec.texture==null)return;
		String texture=spec.texture+owner.getRandom().nextInt(Math.max(1,spec.numTexture));
		// GunSmoke stores the barrel's world yaw/pitch, not the current animated gun matrix.
		var origin=FmumMuzzlePose.smokeOrigin(point,roll);
		// Keep the absolute location in doubles; model matrices only ever see camera-relative floats.
		var position=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().add(origin.m30(),origin.m31(),origin.m32());
		origin.setTranslation(0,0,0);
		if(SMOKE.size()>=128)SMOKE.removeFirst();
		SMOKE.addLast(new WorldSmoke(new FmumSmoke(spec.model,texture,origin,spec.scale,spec.scaleEx,spec.vanishConst),position));
	}
	public static void tick() {
		for(var smoke:SMOKE)smoke.effect.tick();
		SMOKE.removeIf(smoke->!smoke.effect.alive());
		// Cases collide with the world now, so the tick needs the level and the world-space
		// origin their local position is relative to.
		var level=Minecraft.getInstance().level;
		for(var casing:CASES)casing.effect.tick(level,casing.origin.x,casing.origin.y,casing.origin.z);
		CASES.removeIf(casing->!casing.effect.alive());
	}
	public static void reset() { SMOKE.clear();CASES.clear();renderingEntity=null;localSlot=-1;localGun=null; }
	private static boolean inHandPass() {
		var mc=Minecraft.getInstance();
		return mc.player!=null && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui
				&& InfoType.getType(mc.player.getMainHandItem()) instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG;
	}
	@SubscribeEvent
	public static void worldSmoke(RenderLevelStageEvent event) {
		if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
		renderingEntity=null;
		boolean hand=inHandPass();
		boolean worldSmoke=ShaderSmokeRenderer.inWorld(hand,ShaderMuzzleRenderer.shadersEnabled());
		// Cases are world objects, so they are drawn here whichever camera mode is active.
		if((SMOKE.isEmpty() || !worldSmoke) && CASES.isEmpty())return;
		var mc=Minecraft.getInstance();var buffer=mc.renderBuffers().bufferSource();
		// The shader already includes the level view; account for it exactly once.
		var pose=new PoseStack();pose.mulPose(FmumWorldEffectsPose.worldStage(event.getModelViewMatrix(),RenderSystem.getModelViewMatrix()));
		if(worldSmoke)renderSmoke(buffer,pose,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
		renderCases(buffer,pose,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,mc.getTimer().getGameTimeDeltaPartialTick(true));
		buffer.endBatch();
	}
	private static void renderSmoke(MultiBufferSource buffers,int light,int overlay) {
		renderSmoke(buffers,new PoseStack(),light,overlay);
	}
	private static void renderSmoke(MultiBufferSource buffers,PoseStack pose,int light,int overlay) {
		var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera().getPosition();
		boolean shaders=ShaderMuzzleRenderer.shadersEnabled();
		for(var entry:SMOKE) {
			var smoke=entry.effect;
			var texture=Textures.of(smoke.texture,false);
			var rig=ModelCache.rigForReference(smoke.model,1F,texture);
			if(texture==null || rig==null || rig.quads.isEmpty())continue;
			pose.pushPose();pose.translate(entry.position.x-camera.x,entry.position.y-camera.y,entry.position.z-camera.z);pose.mulPose(smoke.pose());
			var type=shaders?ShaderSmokeRenderer.type(texture):RenderType.entityTranslucent(texture);
			int smokeLight=shaders && mc.level!=null?net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,
					net.minecraft.core.BlockPos.containing(entry.position)):light;
			RigRenderer.render(pose,buffers.getBuffer(type),rig,smokeLight,overlay,1F,1F,1F,smoke.alpha,false);
			pose.popPose();
		}
	}
	private static void renderCases(MultiBufferSource buffers,PoseStack pose,int overlay,float partial) {
		var mc=Minecraft.getInstance();
		var camera=mc.gameRenderer.getMainCamera().getPosition();
		for(var entry:CASES) {
			var casing=entry.effect;var type=casing.type;
			var texture=Textures.of(type.bulletTexture,false);
			var rig=ModelCache.rigForReference(type.bulletModel,1F,texture);
			if(texture==null || rig==null || rig.quads.isEmpty())continue;
			// Lit by the light where the case lies, not by the player's hand. A case on the ground
			// at night is dark, and one under a lamp is bright, which is what makes it read as an
			// object in the world rather than an overlay.
			int caseLight=mc.level==null?LightTexture.FULL_BRIGHT
					:net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,
							net.minecraft.core.BlockPos.containing(entry.origin));
			pose.pushPose();pose.translate(entry.origin.x-camera.x,entry.origin.y-camera.y,entry.origin.z-camera.z);pose.mulPose(casing.pose(partial));
			RigRenderer.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),rig,caseLight,overlay,1F,1F,1F,1F,false);
			pose.popPose();
		}
	}
}
