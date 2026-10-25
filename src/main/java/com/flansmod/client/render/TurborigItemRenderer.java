package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.client.GunAnimationRegistry;
import com.flansmod.client.SprintStance;
import com.flansmod.client.model.GunAnimations;
import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelGeometry;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.FlansMod;
import com.flansmod.common.GunMagazine;
import com.flansmod.common.types.DriveableType;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GrenadeType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.ItemFlan;
import com.flansmod.common.types.ModifiableType;
import com.flansmod.common.types.PlaneType;
import com.flansmod.common.types.VehicleType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.flansmod.client.SightFoldAnimations;

/**
 * Draws a content-pack item the way 1.7.10 did.
 *
 * <p>The old mod registered an {@code IItemRenderer} for exactly five kinds of item — guns,
 * grenades, planes, vehicles and mechas — and every one of those renderers was a hand-written
 * {@code GL11} transform followed by the model drawn at 1/16 scale. Nothing else in the pack
 * (attachments, parts, bullets, tools, armour) was ever drawn in 3D; those stayed flat icons.
 *
 * <p>This class is a direct transcription of that switch. The transform values below are copied
 * from {@code RenderGun}/{@code RenderGrenade}/{@code RenderPlane}/{@code RenderVehicle}'s
 * {@code renderItem} methods, in the same order they were applied, because that order is what the
 * packs' models are authored against. The item model JSON deliberately carries no {@code display}
 * block: 1.7.10 never consulted one, so any transform baked in there is a second, conflicting
 * pose, which is what left guns floating beside the player's leg.
 *
 * <p>One adjustment is unavoidable. Modern item rendering shifts the pose by half a block
 * ({@code ItemRenderer.render} ends with {@code translate(-0.5, -0.5, -0.5)}) before handing it to
 * a custom renderer; 1.7.10 had no such shift. Undoing it at the top restores the exact space the
 * old transforms expect.
 */
public class TurborigItemRenderer extends BlockEntityWithoutLevelRenderer
{
	private static TurborigItemRenderer instance;

	/** Definitions whose texture has already been reported, so the diagnostic fires once each. */
	private static final java.util.Set<String> TEXTURE_REPORTED =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Which keys the pose probe has already printed, so each label logs once per tick. The probe is
	 * armed by a system property and fires only around the capture ticks, so its output lines up
	 * with the screenshots rather than with whatever the first frame happened to draw.
	 */
	private static final java.util.Set<String> POSE_PROBED =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** True inside the probe windows around the harness's screenshot ticks (158-162, 188-192). */
	private static boolean probeWindow()
	{
		if(!Boolean.getBoolean("flansmod.probePose"))
			return false;
		net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
		if(p == null)
			return false;
		int t = p.tickCount;
		return (t >= 158 && t <= 162) || (t >= 188 && t <= 192);
	}

	/**
	 * Prints the rig's box in true view space (camera at the origin, looking down -Z).
	 *
	 * <p>First-person hand rendering puts the INVERSE PROJECTION at the bottom of the pose stack
	 * and the projection on the model-view stack — a vanilla trick whose net effect is that the
	 * chain above it is already view-relative. Reading the pose matrix alone therefore reads
	 * {@code P⁻¹·chain}, and its numbers mean nothing on their own; multiplying the projection
	 * back on the left recovers the view-space box, which is what the frustum test below checks.
	 */
	private static void probePose(String label, PoseStack pose, BakedRig rig)
	{
		net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
		int t = p == null ? -1 : p.tickCount;
		if(!POSE_PROBED.add(label + "/" + t))
			return;
		org.joml.Matrix4f view = new org.joml.Matrix4f(
				com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
				.mul(pose.last().pose(), new org.joml.Matrix4f());
		org.joml.Vector3f v = new org.joml.Vector3f();
		float ax = Float.MAX_VALUE, ay = Float.MAX_VALUE, az = Float.MAX_VALUE;
		float bx = -Float.MAX_VALUE, by = -Float.MAX_VALUE, bz = -Float.MAX_VALUE;
		float[][] corners = {
				{ rig.minX, rig.minY, rig.minZ }, { rig.minX, rig.minY, rig.maxZ },
				{ rig.minX, rig.maxY, rig.minZ }, { rig.minX, rig.maxY, rig.maxZ },
				{ rig.maxX, rig.minY, rig.minZ }, { rig.maxX, rig.minY, rig.maxZ },
				{ rig.maxX, rig.maxY, rig.minZ }, { rig.maxX, rig.maxY, rig.maxZ } };
		for(float[] c : corners)
		{
			v.set(c[0], c[1], c[2]);
			view.transformPosition(v);
			ax = Math.min(ax, v.x); ay = Math.min(ay, v.y); az = Math.min(az, v.z);
			bx = Math.max(bx, v.x); by = Math.max(by, v.y); bz = Math.max(bz, v.z);
		}
		// Frustum at the near end of the box: on-screen means x/z and y/z inside the FOV's slope
		// and z negative (in front). Reported as a plain yes/no so the log answers the question.
		double aspect = (double)Minecraft.getInstance().getWindow().getWidth()
				/ Math.max(1, Minecraft.getInstance().getWindow().getHeight());
		double tanHalf = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get() / 2F));
		float nearZ = bz;   // most negative of the corners is the near face
		boolean onScreen = az < -0.01F && bx > -(-nearZ) * tanHalf * aspect && ax < (-nearZ) * tanHalf * aspect
				&& by > -(-nearZ) * tanHalf && ay < (-nearZ) * tanHalf;
		FlansMod.LOGGER.info(String.format(
				"[PoseProbe] t=%d %s viewBox x[%.3f..%.3f] y[%.3f..%.3f] z[%.3f..%.3f] onScreen=%s",
				t, label, ax, bx, ay, by, az, bz, onScreen));
	}

	public TurborigItemRenderer()
	{
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
				Minecraft.getInstance().getEntityModels());
	}

	public static TurborigItemRenderer get()
	{
		if(instance == null)
			instance = new TurborigItemRenderer();
		return instance;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack pose,
							 MultiBufferSource buffers, int light, int overlay)
	{
		if(!(stack.getItem() instanceof ItemFlan flan))
			return;

		InfoType type = flan.type;
		if(type instanceof GunType gun) type = com.flansmod.common.GunAttachments.view(gun,stack);
		var folding=type instanceof GunType gun?SightFoldAnimations.forStack(stack,gun,displayContext,
				Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)):SightFoldAnimations.Frame.IDLE;

		// The inventory draws the pack's flat icon, which is what 1.7.10 did: none of its
		// renderers claimed the inventory context, so vanilla drew the item's icon there.
		if(displayContext == ItemDisplayContext.GUI || !type.rendersInHand())
		{
			renderIcon(stack, pose, buffers, light, overlay);
			return;
		}

		BakedRig rig = ModelCache.rigFor(type, type.modelScale);
		if(rig == null || rig.quads.isEmpty())
		{
			renderIcon(stack, pose, buffers, light, overlay);
			return;
		}

		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(textureFor(type)));
		if(buffer == null)
			return;

		// One line per definition, reporting the skin the pack asked for and whether the pack
		// really ships it. Without this a wrong texture is invisible in the log — the renderer
		// simply falls back to the missing-pack marker, which is a plausible-looking dark image and
		// is exactly how the unparsed Texture key went unnoticed for so long.
		if(com.flansmod.common.FlansMod.LOGGER.isDebugEnabled() && TEXTURE_REPORTED.add(type.shortName))
		{
			ResourceLocation wanted = textureFor(type);
			boolean present = Minecraft.getInstance().getResourceManager().getResource(wanted).isPresent();
			com.flansmod.common.FlansMod.LOGGER.info("[GunTexture] " + type.shortName
					+ " Texture=" + type.texture + " -> " + wanted + " present=" + present);
		}

		boolean composeSights=type instanceof GunType && displayContext.firstPerson();
		if(composeSights)FmumSightRenderer.beginWeapon((GunType)type);
		pose.pushPose();
		try {
		var recoilFrame=type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && displayContext.firstPerson()
				?GunAnimationRegistry.right(Minecraft.getInstance().player).fmumRecoil.frame(
						Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true),SHAKE_RANDOM)
				:com.flansmod.client.model.FmumRecoil.Frame.ZERO;
		// Undo the half-block shift modern item rendering applies; see the class comment.
		pose.translate(0.5F, 0.5F, 0.5F);
		boolean mirrored = applyLegacyTransform(pose, type, displayContext, rig,recoilFrame);

		// The whole-gun reload pose, sitting between the resting pose and the parts, which is where
		// the original's first-person case put it. Only guns have it, and only in first person.
		if(type instanceof GunType && displayContext.firstPerson() && !FmumFirstPersonPose.hasPose(rig.animation))
		{
			GunAnimations animation = GunAnimationRegistry.right(Minecraft.getInstance().player);
			if(animation.reloading)
			{
				float smoothing = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
				applyReloadGunPose(pose, rig, reloadProgress(rig, animation, smoothing),
						displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1F : 1F);
			}
		}

		// FMUM recoil translation/rotation was applied in placement; now follow the view axes.
		if(type instanceof GunType && displayContext.firstPerson())
		{
			GunAnimations animation = GunAnimationRegistry.right(Minecraft.getInstance().player);
			float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
			if(FmumFirstPersonPose.hasPose(rig.animation)) {
				var action=actionFrame(partial);
				float ads=FmumActionPose.ads(rig.animation,action,AimState.blend(partial)),sprint=SprintStance.blend(partial);
				var basis=FmumFirstPersonPose.rotation(rig.animation,ads,sprint,0F,action)
						.mul(FmumFirstPersonPose.recoilRotation(rig.animation,reloadProgress(rig,animation,partial),recoilFrame));
				pose.mulPose(FmumActionPose.follow(new org.joml.Matrix4f(),basis,
						com.flansmod.client.ClientGunMotion.frame(rig.animation,partial,ads,sprint)));
			}
			else applyRecoil(pose,rig,animation,partial);
		}

		// Sight pivot follows recoil, before both gun geometry and arms (EFT core 1.1). The target is
		// the camera's actual position in gun space, not 1.7.10's fixed 0.4, because that constant
		// only matches the one pose the pack tuned it against (see FmumFirstPersonPose.aim).
		if(type instanceof GunType gun && displayContext.firstPerson() && FmumFirstPersonPose.hasPose(rig.animation))
		{
			float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
			float aimAds=FmumActionPose.ads(rig.animation,actionFrame(partial),AimState.blend(partial));
			var eye=FmumFirstPersonPose.eyeInGunSpace(new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose()));
			pose.mulPose(FmumFirstPersonPose.aim(new org.joml.Matrix4f(),folding.active()?folding.aimView():gun,
					aimAds,eye));
		}
		if(displayContext.firstPerson() && folding.active())pose.mulPose(SightFoldPose.weapon(new org.joml.Matrix4f(),folding.progress()));
		// Diagnostic: at the capture ticks, dump the gun's pose matrix and its box in view space.
		if(probeWindow())
			probePose("gun:" + type.shortName, pose, rig);

		if(type instanceof AttachmentType)
			renderAttachmentSurface(pose,buffers,rig,textureFor(type),0,light,overlay,mirrored);
		else if(rig.quadsByPart.size() > 1)
			renderAnimatedParts(pose, buffer, rig, type, stack, displayContext, light, overlay, mirrored);
		else
			RigRenderer.render(pose, buffer, rig, light, overlay, 1F, 1F, 1F, 1F, mirrored);

		// The parts the gun is carrying. A pack describes these as a tree per slot, and an EFT
		// rifle's barrel, handguard, stock, dust cover and sights all live here — without this the
		// player holds the bare receiver. Drawn inside the same pose as the gun body, so the whole
		// assembly moves together.
		if(type instanceof com.flansmod.common.types.GunType gun && !gun.defaultAttachments.isEmpty())
		{
			renderAttachments(pose,buffers,gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),mirrored,light,overlay,
					displayContext.firstPerson()?partMotion(gun,rig,stack):null,folding,new int[0],gun.fireModeIndex(stack),
					stack,displayContext.firstPerson());
		}
		if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG)
			renderMagazine(pose,buffers,gun,stack,rig,displayContext.firstPerson(),mirrored,light,overlay);
		if(type instanceof GunType gun)
			renderSeatedRounds(pose,buffers,gun,stack,rig,mirrored,light,overlay);

		// The pack's own arms, when it ships them. EFT writes ArmModel on every gun, and this is
		// what puts a hand on the handguard — the difference between "a rifle is being held" and
		// "a rifle is floating in the corner of the screen". FMUM draws these last, inside the
		// same pose, and only in first person: in third person the player's own model arm shows.
		if(type instanceof com.flansmod.common.types.GunType gun && displayContext.firstPerson())
			renderArms(pose, buffers, gun, rig, mirrored, light, overlay,folding);
		if(type instanceof GunType gun)
			MuzzleEffectsRenderer.render(pose,buffers,gun,stack,displayContext,rig.animation,light,overlay,mirrored);
		if(composeSights)FmumSightRenderer.finishWeapon(buffers);
		} finally {
			if(composeSights)FmumSightRenderer.cancelWeapon();
			pose.popPose();
		}
	}

	/**
	 * Draws the first-person arms — the PACK'S OWN arm model, posed exactly as FMUM 1.0 posed it.
	 *
	 * <p>FMUM's first-person renderer looked up {@code gunType.leftArmModel/rightArmModel} (the
	 * pack's {@code ArmModel} line, which every EFT gun sets) and drew that mesh inside the gun's
	 * own pose with this chain:
	 *
	 * <pre>  T(leftArmPos) · R_x(leftArmRot.x) · R_y(leftArmRot.y) · R_z(leftArmRot.z) · S(leftArmScale)</pre>
	 *
	 * <p>Three details matter and all three are easy to get wrong:
	 * <ul>
	 *   <li>the translation comes FIRST, then the rotations. GL composes right-to-left, so the
	 *       offset lands the arm's anchor in gun space and the rotations then swing the limb about
	 *       that anchor. Rotating before translating moves the anchor itself and throws the arm
	 *       out of frame — which is what the first attempt did.</li>
	 *   <li>the mesh is the PACK'S, not the vanilla player arm. EFT's {@code EFTArm} is a 4×12×4
	 *       box whose local SRG rotations turn its length onto X (-11..+1), so the limb extends
	 *       from the anchor in its authored direction. The vanilla arm is authored differently,
	 *       and posing it with these offsets aims the limb away from the weapon — the second
	 *       attempt, which put a pale column in the sky.</li>
	 *   <li>the arm is drawn OUTSIDE the gun's {@code ModelScale} push — FMUM popped that matrix
	 *       before drawing arms — so the offsets are read in 1/16 units while the gun geometry
	 *       sits at {@code modelScale/16}. The rig is baked at scale 1 for this reason.</li>
	 * </ul>
	 *
	 * <p>This is the same chain the offline {@code RenderAssembly} preview applies, and that
	 * preview is where the placement was verified against the EFT pack.
	 */
	private static void renderArms(PoseStack pose, MultiBufferSource buffers, GunType gun, BakedRig gunRig,
								   boolean mirrored, int light, int overlay,SightFoldAnimations.Frame folding)
	{
		drawArm(pose, buffers, gun, gunRig, gun.leftArmModel, gun.leftArmTexture,
				"leftArmPos", "leftArmRot", "leftArmScale", mirrored, light, overlay,folding);
		drawArm(pose, buffers, gun, gunRig, gun.rightArmModel, gun.rightArmTexture,
				"rightArmPos", "rightArmRot", "rightArmScale", mirrored, light, overlay,folding);
	}

	/** One arm: bake the pack's mesh, apply the model class's pose, bind a skin, draw. */
	private static void drawArm(PoseStack pose, MultiBufferSource buffers, GunType gun, BakedRig gunRig,
								String modelRef, String textureName,
								String posKey, String rotKey, String scaleKey,
								boolean mirrored, int light, int overlay,SightFoldAnimations.Frame folding)
	{
		if(modelRef == null || modelRef.isBlank() || modelRef.equalsIgnoreCase("None"))
			return;

		ModelGeometry.Model model = gunRig.animation;
		float[] pos = model == null ? null : model.fmumPose.get(posKey);
		float[] rot = model == null ? null : model.fmumPose.get(rotKey);
		float[] scale = model == null ? null : model.fmumPose.get(scaleKey);
		if(pos == null && rot == null)
			return;

		// The pack's own arm texture when the ArmModel line names one, otherwise the player's
		// skin — FMUM's choice, and the reason an EFT arm matches the player wearing it.
		ResourceLocation skin = null;
		if(textureName != null && !textureName.isBlank() && !textureName.equalsIgnoreCase("None"))
			skin = Textures.of(textureName, false);
		if(skin == null && Minecraft.getInstance().player != null)
			skin = Minecraft.getInstance().player.getSkin().texture();
		if(skin == null)
			return;

		// Baked at scale 1 (see renderArms) and against the skin actually being drawn, so the
		// 64x32 arm model's UVs are corrected onto the modern 64x64 player skin.
		BakedRig armRig = ModelCache.rigForReference(modelRef, 1F, skin);
		if(armRig == null || armRig.quads.isEmpty())
			return;

		pose.pushPose();
		float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		var armPose=FmumReloadPose.arm(new org.joml.Matrix4f(),model,posKey.startsWith("left"),
				reloadFrame(partial),actionFrame(partial),gun.modelScale,gun);
		if(posKey.startsWith("left"))armPose=SightFoldRenderer.arm(armPose,folding,gun);
		pose.mulPose(armPose);

		// Diagnostic: at the capture ticks, dump the arm's pose matrix and its box in view space.
		if(probeWindow())
			probePose("arm:" + posKey + ":" + modelRef, pose, armRig);

		VertexConsumer armBuffer = buffers.getBuffer(RenderType.entityTranslucent(skin));
		if(armBuffer != null)
			RigRenderer.render(pose, armBuffer, armRig, light, overlay, 1F, 1F, 1F, 1F, mirrored);
		pose.popPose();

		// Controls for the same diagnostic, drawn through the flat white texture so what shows is
		// geometry and pose only — never skin alpha or UV placement: one at the anchor with no
		// rotation (red), one fully posed (blue). Red visible but not blue means the rotations
		// carry the arm off-frame; blue visible but no skin arm means the skin side fails; both
		// missing while the gun draws means the arm draw itself is dropped.
		if(probeWindow())
		{
			ResourceLocation white = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
			VertexConsumer ctl = buffers.getBuffer(RenderType.entityCutoutNoCull(white));
			pose.pushPose();
			if(pos != null)
				pose.translate(pos[0], pos[1], pos[2]);
			probePose("armCTL-anchor:" + posKey + ":" + modelRef, pose, armRig);
			if(ctl != null)
				RigRenderer.render(pose, ctl, armRig, light, overlay, 1F, 0.15F, 0.15F, 1F, mirrored);

			if(rot != null)
			{
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot[0]));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot[1]));
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot[2]));
			}
			if(scale != null)
				pose.scale(scale[0], scale[1], scale[2]);
			probePose("armCTL-posed:" + posKey + ":" + modelRef, pose, armRig);
			if(ctl != null)
				RigRenderer.render(pose, ctl, armRig, light, overlay, 0.15F, 0.15F, 1F, 1F, mirrored);
			pose.popPose();
		}
	}
	/**
	 * Draws one slot's attachments and everything they carry.
	 *
	 * <p>Transcribed from FMUM's {@code renderAttachments_FP}, whose per-node transform is:
	 *
	 * <pre>  T(slot.attachPos + node.pos) · R_x(slot.attachRot) · draw(part) · recurse(children)</pre>
	 *
	 * where {@code node.pos} walks the part along the slot's own step vector, and children are
	 * positioned by the PART's {@code AttachPoints} rather than the gun's — which is what lets an
	 * AK's dust cover mount its own rear sight. The positions are already in blocks: the loader
	 * multiplied them by the owner's model scale when the file was parsed, which is the same place
	 * FMUM applied it.
	 */
	private static void renderAttachments(PoseStack pose, MultiBufferSource buffers, ModifiableType owner,
										  java.util.List<ModifiableType.DefaultAttachment> nodes,
										  boolean mirrored,int light,int overlay,PartMotion motion,SightFoldAnimations.Frame folding,int[] parent,int fireMode,
										  ItemStack stack,boolean firstPerson)
	{
		var indices=new java.util.HashMap<Integer,Integer>();
		for(ModifiableType.DefaultAttachment node : nodes)
		{
			int[] path=java.util.Arrays.copyOf(parent,parent.length+2);
			path[parent.length]=node.slot;path[parent.length+1]=indices.merge(node.slot,1,Integer::sum)-1;
			com.flansmod.common.types.AttachmentType at =
					com.flansmod.common.types.AttachmentType.getAttachment(node.name);
			if(at == null)
				continue;

			pose.pushPose();
			if(node.slot >= 0 && node.slot < owner.slots.length)
			{
				ModifiableType.AttachableSlot slot = owner.slots[node.slot];
				pose.translate(slot.x + node.pos, slot.y, slot.z);
				if(slot.rotate != 0F)
					pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(slot.rotate));
			}
			if(motion!=null) {
				var m=motion.rig.animation; float scale=motion.gun.modelScale;
				switch(at.itsType.toLowerCase(java.util.Locale.ROOT)) {
					case "bolt" -> pose.translate(motion.slide*scale,0F,0F);
					case "charginghandle", "charging_handle" -> pose.translate(FmumReloadPose.charge(m,motion.reload,motion.action).handle()*scale,0F,0F);
					case "altchargehandle", "alt_charge_handle" -> pose.mulPose(FmumActionPose.pump2(new org.joml.Matrix4f(),m,FmumReloadPose.charge(m,motion.reload,motion.action),scale,false));
					case "piston" -> pose.translate(-motion.shotSlide*FmumActionPose.scalar(m,"altgunSlideDistance",0F)*scale,0F,0F);
					default -> { }
				}
			}

			BakedRig partRig = ModelCache.rigFor(at, at.modelScale);
			var foldPart=folding.part(path);
			boolean animated=foldPart!=null && SightFoldRenderer.render(pose,buffers,foldPart,folding.phase(),light,overlay,mirrored,motion!=null,fireMode);
			if(!animated && partRig != null && !partRig.quads.isEmpty())
			{
				// An attachment model carries conditional meshes beside its body — a flashlight
				// cone and a fire-mode indicator. 1.7.10 drew those only when their state was on,
				// so drawing the whole rig here would leave a cone and selector permanently
				// visible. ModelAttachment.render() draws exactly one array, and so does this.
				renderAttachmentSurface(pose,buffers,partRig,textureFor(at),fireMode,light,overlay,mirrored);
			}
			// Tactical devices are separate glow models, not part of the attachment body: 1.7.10
			// renders the flashlight lens / laser emitter only while its switch is on.
			renderTacticalDevice(pose,buffers,at,partRig,stack,firstPerson,overlay,mirrored);

			// The sight's reticle model. 1.7.10 kept it in a second model array and drew it only
			// while the gun was raised; it is not part of the attachment's own mesh, so it cannot
			// come from the bake above.
			if(motion!=null && !folding.active()) {
				// The scope prepares a nested world render from inside this hand pass. Anything
				// still queued in the shared BufferSource would be batched into the scope target
				// with the world camera, which showed up as a huge receiver/attachment inside the
				// ocular after a left-side device was installed. Flush our geometry into the real
				// target first; the scope's own guard then only has to handle the sight itself.
				if(buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source)
					source.endBatch();
				FmumSightRenderer.render(pose,buffers,at,node,motion.gun,partRig,overlay,mirrored);
			}

			if(!node.children.isEmpty()) {
				pose.pushPose();
				if(foldPart!=null)pose.mulPose(SightFoldRenderer.children(foldPart,folding.phase(),at));
				renderAttachments(pose,buffers,at,node.children,mirrored,light,overlay,motion,folding,path,fireMode,stack,firstPerson);
				pose.popPose();
			}

			pose.popPose();
		}
	}

	/** The source model's ordinary surface plus its proven glowOn/off lightmap sections. */
	private static void renderAttachmentSurface(PoseStack pose, MultiBufferSource buffers, BakedRig rig,
			ResourceLocation texture, int fireMode, int light, int overlay, boolean mirrored)
	{
		var buffer = buffers.getBuffer(RenderType.entityCutout(texture));
		if(buffer == null) return;
		var body = rig.attachmentBody(fireMode);
		if(!body.isEmpty()) RigRenderer.renderPart(pose,buffer,body,light,overlay,1F,1F,1F,1F,mirrored);
		// A named flashlight/laser/reticle array alone never opts into the ordinary body stage.
		for(var emission : rig.emissivePasses(fireMode))
			RigRenderer.renderPart(pose,buffer,emission.quads(),emission.light().apply(light),
					overlay,1F,1F,1F,1F,mirrored);
	}

	/**
	 * The flashlight's lens model and the laser emitter model, both drawn full-bright while their
	 * stack switches are on. 1.7.10 keeps them in separate model arrays so they never become part
	 * of the body; the laser is offset by the attachment model's authored {@code laserFrom} point.
	 */
	private static void renderTacticalDevice(PoseStack pose, MultiBufferSource buffers,
			com.flansmod.common.types.AttachmentType at, BakedRig partRig, ItemStack stack,
			boolean firstPerson, int overlay, boolean mirrored)
	{
		if(stack == null || stack.isEmpty() || at == null)
			return;
		var texture = textureFor(at);
		if(at.flashlight && com.flansmod.common.GunAttachments.isLightOn(stack) && at.lightModel != null)
		{
			var lightRig = ModelCache.rigForReference(at.lightModel, at.modelScale, texture);
			if(lightRig != null && !lightRig.quads.isEmpty())
				RigRenderer.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), lightRig,
						net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 1F, 1F, 1F, 1F, mirrored);
		}
		if(at.laserPointer && com.flansmod.common.GunAttachments.isLaserOn(stack))
		{
			String model = firstPerson ? at.laserModelFP : at.laserModel;
			if(model == null)
				model = at.laserModelFP != null ? at.laserModelFP : at.laserModel;
			if(model == null)
				return;
			var laserRig = ModelCache.rigForReference(model, at.modelScale, texture);
			if(laserRig == null || laserRig.quads.isEmpty())
				return;
			float[] from = FmumActionPose.vector(partRig == null ? null : partRig.animation, "laserFrom");
			pose.pushPose();
			// 1.7.10: glScalef(modelScale) then glTranslatef(laserFrom), so the authored point is in
			// model units and the world offset is that point times the part's model scale.
			pose.translate(from[0] * at.modelScale, from[1] * at.modelScale, from[2] * at.modelScale);
			RigRenderer.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(texture)), laserRig,
					net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 1F, 1F, 1F, 1F, mirrored);
			pose.popPose();
		}
	}

	private static com.flansmod.common.GunActionState.Frame actionFrame(float partial) {
		var player=Minecraft.getInstance().player;
		var data=player==null?null:com.flansmod.common.PlayerHandler.getPlayerData(player);
		return data==null?com.flansmod.common.GunActionState.Frame.IDLE:data.gunAction.frame(partial);
	}
	private static com.flansmod.common.GunReloadState.Frame reloadFrame(float partial) {
		var player=Minecraft.getInstance().player;
		var data=player==null?null:com.flansmod.common.PlayerHandler.getPlayerData(player);
		return data==null || data.reloadGun!=InfoType.getType(player.getMainHandItem())
				?com.flansmod.common.GunReloadState.Frame.IDLE:data.gunReload.frame(partial);
	}
	private record PartMotion(GunType gun,BakedRig rig,com.flansmod.common.GunActionState.Frame action,
			com.flansmod.common.GunReloadState.Frame reload,float slide,float shotSlide) { }
	private static PartMotion partMotion(GunType gun,BakedRig rig,ItemStack stack) {
		float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		var animation=GunAnimationRegistry.right(Minecraft.getInstance().player);
		float shot=lerp(animation.lastGunSlide,animation.gunSlide,partial);
		var action=actionFrame(partial);
		var reload=reloadFrame(partial);
		return new PartMotion(gun,rig,action,reload,FmumReloadPose.slide(rig.animation,reload,action,-shot*rig.animation.gunSlideDistance,
				com.flansmod.common.GunActions.boltCaught(stack)),shot);
	}
	private static void renderMagazine(PoseStack pose,MultiBufferSource buffers,GunType gun,ItemStack stack,
			BakedRig gunRig,boolean firstPerson,boolean mirrored,int light,int overlay) {
		float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		var reload=firstPerson?reloadFrame(partial):com.flansmod.common.GunReloadState.Frame.IDLE;
		ItemStack magazine=GunMagazine.get(stack,gun,0);
		var player=Minecraft.getInstance().player;
		var data=player==null?null:com.flansmod.common.PlayerHandler.getPlayerData(player);
		if(data!=null && reload.step()==com.flansmod.common.GunReloadState.Step.LOAD_MAG) magazine=data.reloadIncomingMagazine;
		else if(data!=null && reload.step()==com.flansmod.common.GunReloadState.Step.UNLOAD_MAG) magazine=data.reloadOutgoingMagazine;
		GunType mag=InfoType.getType(magazine) instanceof GunType g && g.itsType==GunType.GunItsType.MAG?g:null;
		if(mag==null || !FmumFirstPersonPose.hasPose(gunRig.animation)) return;
		BakedRig rig=ModelCache.rigFor(mag,mag.modelScale);
		if(rig==null || rig.quads.isEmpty()) return;
		pose.pushPose();
		pose.mulPose(FmumReloadPose.magazine(new org.joml.Matrix4f(),gunRig.animation,reload,firstPerson
				?actionFrame(partial)
				:com.flansmod.common.GunActionState.Frame.IDLE));
		renderSeatedRounds(pose,buffers,mag,magazine,rig,mirrored,light,overlay);
		RigRenderer.render(pose,buffers.getBuffer(RenderType.entityCutout(textureFor(mag))),rig,light,overlay,1F,1F,1F,1F,mirrored);
		pose.popPose();
	}
	/** Uses BulletModel/BulletTexture/BulletScale, distinct from the in-flight projectile model. */
	private static void renderSeatedRounds(PoseStack pose,MultiBufferSource buffers,GunType type,ItemStack stack,
			BakedRig rig,boolean mirrored,int light,int overlay) {
		if(type.itsType==GunType.GunItsType.MAG) renderMagazineFollower(pose,buffers,type,stack,rig,mirrored,light,overlay);
		ItemStack round=type.itsType==GunType.GunItsType.MAG?GunMagazine.chamberedBullet(stack):GunMagazine.getChamberedRound(stack);
		if(round.isEmpty() && type.itsType!=GunType.GunItsType.MAG) round=GunMagazine.get(stack,type,0);
		if(!(InfoType.getType(round) instanceof com.flansmod.common.types.BulletType bullet)) return;
		int rounds=type.itsType==GunType.GunItsType.MAG?GunMagazine.roundsRemaining(stack):1;
		for(var seat:FmumBulletPose.seats(rig.animation,rounds)) {
			var actual=type.itsType==GunType.GunItsType.MAG?InfoType.getType(GunMagazine.magazineRoundAt(stack,type,seat.ammunitionIndex())):bullet;
			if(!(actual instanceof com.flansmod.common.types.BulletType seated)) continue;
			ResourceLocation texture=Textures.of(seated.bulletTexture,false);
			if(texture==null || seated.bulletModel==null) continue;
			BakedRig bulletRig=ModelCache.rigForReference(seated.bulletModel,seated.bulletScale,texture);
			if(bulletRig==null) continue;
			pose.pushPose();pose.mulPose(seat.pose());
			RigRenderer.render(pose,buffers.getBuffer(RenderType.entityCutout(texture)),bulletRig,light,overlay,1F,1F,1F,1F,mirrored);
			pose.popPose();
		}
	}

	private static void renderMagazineFollower(PoseStack pose,MultiBufferSource buffers,GunType type,ItemStack stack,
			BakedRig magazineRig,boolean mirrored,int light,int overlay) {
		if(type.magFollowerModel==null) return;
		var position=FmumBulletPose.follower(magazineRig.animation,GunMagazine.roundsRemaining(stack));
		if(position==null) return;
		var texture=Textures.forMagazineFollower(type);
		if(texture==null) return;
		var follower=ModelCache.rigForReference(type.magFollowerModel,type.magFollowerScale,texture);
		if(follower==null || follower.quads.isEmpty()) return;
		pose.pushPose();pose.mulPose(position);
		RigRenderer.render(pose,buffers.getBuffer(RenderType.entityCutout(texture)),follower,light,overlay,1F,1F,1F,1F,mirrored);
		pose.popPose();
	}

	/**
	 * Draws a gun part by part, applying the animation transforms 1.7.10's {@code RenderGun}
	 * applied — the slide sliding back on firing, the pump travelling, the clip tilting out.
	 *
	 * <p>The parts are named by the field they were built into, and the names are the contract:
	 * {@code slideModel} is the slide, {@code pumpModel} the pump, {@code ammoModel} the clip. A
	 * model that has none of them falls through to the single-pass path above.
	 *
	 * <p>Each transform below is one block of the original {@code renderGun}, in the same order and
	 * with the same constants — the slide's {@code gunSlideDistance}, the pump's
	 * {@code pumpHandleDistance}, the break action's {@code barrelBreakPoint} and {@code breakAngle}.
	 */
	private static void renderAnimatedParts(PoseStack pose, VertexConsumer buffer, BakedRig rig,
											InfoType type, ItemStack stack, ItemDisplayContext displayContext,
											int light, int overlay, boolean mirrored)
	{
		GunAnimations animation = GunAnimationRegistry.right(Minecraft.getInstance().player);
		// Partial tick, so the animation moves between ticks rather than stepping at 20 Hz.
		float smoothing = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
		boolean reload = animation.reloading;
		float reloadRotate = reloadProgress(rig, animation, smoothing);

		// The interpolated reload progress, from which the original derived two more curves: the
		// clip's out-and-back travel, and the "loading only" fraction the tube loaders use.
		float progress = lerp(animation.lastReloadAnimationProgress, animation.reloadAnimationProgress, smoothing);
		float tilt = rig.animation.tiltGunTime;
		float unload = rig.animation.unloadClipTime;
		float load = rig.animation.loadClipTime;
		float clipPosition = 0F;
		if(progress > tilt && progress < tilt + unload)
			clipPosition = (progress - tilt) / unload;
		if(progress >= tilt + unload && progress < tilt + unload + load)
			clipPosition = 1F - (progress - (tilt + unload)) / load;
		float loadOnlyClipPosition = Math.max(0F, Math.min(1F,
				1F - ((progress - tilt) / (unload + load))));

		// Every translate below happens inside the model-scale the original's gun block pushed,
		// so a value from the class is scaled by modelScale before it lands in the pose: the
		// vertices this draws are already at modelScale / 16, and the original's translates were
		// multiplied by the same factor by the GL stack. Without this, a gun authored at quarter
		// scale slides four times too far.
		float scale = safeModelScale(type);
		var action=displayContext.firstPerson()?actionFrame(smoothing):com.flansmod.common.GunActionState.Frame.IDLE;
		var fmumReload=displayContext.firstPerson()?reloadFrame(smoothing):com.flansmod.common.GunReloadState.Frame.IDLE;

		// Tube-loading guns hide the round stack when there is nothing in the gun; the original
		// gated the whole clip on this, reloading or not.
		GunType gun = type instanceof GunType g ? g : null;
		String animType = rig.animation.animationType;
		boolean clipHidden = gun != null
				&& ("END_LOADED".equals(animType) || "BACK_LOADED".equals(animType))
				&& GunMagazine.isEmpty(stack, gun);

		for(Map.Entry<String, List<BakedRig.Quad>> entry : rig.quadsByPart.entrySet())
		{
			String part = entry.getKey();
			List<BakedRig.Quad> quads = entry.getValue();
			boolean skip = false;

			// The fire-mode selector is one array holding every position (safe / semi / auto).
			// 1.7.10's renderSwitch drew only the current mode's slice; drawing the whole array
			// leaves all three levers on the receiver at once.
			if(gun != null && rig.switchModelsPerState() > 0)
			{
				int fireMode = gun.fireModeIndex(stack);
				if("switchModel".equals(part))
					quads = rig.switchModelSlice(fireMode);
				else if("slideSwitchModel".equals(part))
					quads = rig.slideSwitchModelSlice(fireMode);
			}

			pose.pushPose();

			// RenderGun drew the animated parts under their own GL push/pop, each with one
			// transform; these are those transforms, by part name.
			switch(part)
			{
				case "slideModel" -> pose.translate(FmumReloadPose.slide(rig.animation,fmumReload,action,
						-lerp(animation.lastGunSlide,animation.gunSlide,smoothing)*rig.animation.gunSlideDistance,
						displayContext.firstPerson()&&com.flansmod.common.GunActions.boltCaught(stack))*scale,0F,0F);
				case "slideSwitchModel" -> pose.translate(FmumReloadPose.slide(rig.animation,fmumReload,action,
						-lerp(animation.lastGunSlide,animation.gunSlide,smoothing)*rig.animation.gunSlideDistance,
						displayContext.firstPerson()&&com.flansmod.common.GunActions.boltCaught(stack))*scale,0F,0F);
				case "pumpModel" -> pose.translate((action.operation()==com.flansmod.common.GunActionState.Operation.CHECK_CHAMBER || fmumReload.step()==com.flansmod.common.GunReloadState.Step.CHARGE
						?FmumReloadPose.charge(rig.animation,fmumReload,action).handle():-(1F-Math.abs(lerp(animation.lastPumped,animation.pumped,smoothing)))*rig.animation.pumpHandleDistance)*scale,0F,0F);
				case "pumpModel2" -> pose.mulPose(FmumActionPose.pump2(new org.joml.Matrix4f(),rig.animation,FmumReloadPose.charge(rig.animation,fmumReload,action),scale,true));
				case "altSlideModel" -> pose.translate(-lerp(animation.lastGunSlide,animation.gunSlide,smoothing)*FmumActionPose.scalar(rig.animation,"altgunSlideDistance",0F)*scale,0F,0F);
				case "breakActionModel" ->
				{
					float[] pivot = rig.animation.barrelBreakPoint;
					pose.translate(pivot[0] * scale, pivot[1] * scale, pivot[2] * scale);
					pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(reloadRotate * -rig.animation.breakAngle));
					pose.translate(-pivot[0] * scale, -pivot[1] * scale, -pivot[2] * scale);
				}
				case "revolverBarrelModel" ->
				{
					float[] pivot = rig.animation.revolverFlipPoint;
					pose.translate(pivot[0] * scale, pivot[1] * scale, pivot[2] * scale);
					pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(reloadRotate * rig.animation.revolverFlipAngle));
					pose.translate(-pivot[0] * scale, -pivot[1] * scale, -pivot[2] * scale);
				}
				case "minigunBarrelModel" ->
				{
					float[] pivot = rig.animation.minigunBarrelOrigin;
					pose.translate(pivot[0] * scale, pivot[1] * scale, pivot[2] * scale);
					pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(animation.minigunBarrelRotation));
					pose.translate(-pivot[0] * scale, -pivot[1] * scale, -pivot[2] * scale);
				}
				case "ammoModel" ->
				{
					// The clip moves only while reloading, and only in first person — the original
					// gated it on `animations.reloading && thirdPersonView == 0` — and a tube loader
					// with nothing in it is not drawn at all.
					if(clipHidden)
						skip = true;
					else if(reload && displayContext.firstPerson())
						applyClipReload(pose, rig, clipPosition, loadOnlyClipPosition, reloadRotate, scale);
				}
				default -> { }
			}

			if(!skip)
				RigRenderer.renderPart(pose, buffer, quads, light, overlay, 1F, 1F, 1F, 1F, mirrored);
			pose.popPose();
		}
	}

	/**
	 * The whole-gun reload pose, from the switch in 1.7.10's first-person branch.
	 *
	 * <p>Nine of the ten cases are the original's hand-tuned constants — one tilt per magazine
	 * layout, copied verbatim — and the tenth, {@code CUSTOM}, is the pose the model class itself
	 * declares ({@code rotateGunVertical} and friends), which is why those fields exist. The
	 * {@code flip} multiplications are the original's: a left-hand gun tilts the other way.
	 */
	/**
	 * Applies the whole-gun recoil kick, transcribed from the 1.20.1 build's {@code handleGunRecoil}.
	 *
	 * <p>Four transforms, and each has a job:
	 * <ul>
	 *   <li>{@code T(-recoil·recoilSlideDistance, 0, 0)} — the weapon travels back along its own
	 *       axis. {@code RecoilSlideDistance} is per model (the StG-44 sets 0.15 blocks).</li>
	 *   <li>{@code R_z(-recoil·rotateSlideDistance)} — the muzzle climbs, which is the part that
	 *       reads as "kick" rather than "slide". The pack values are negative, so the negation
	 *       turns them into a rise.</li>
	 *   <li>{@code R_y} and {@code R_x} by a random amount scaled by {@code ShakeDistance} — a
	 *       little wobble each shot so a burst does not look like a single repeated frame.</li>
	 * </ul>
	 *
	 * <p>The random value is taken from a triangle-ish distribution in the 1.20.1 build
	 * ({@code nextFloat()·3 - 1.5}), not a Gaussian: it is deliberately flat so the wobble stays
	 * inside a small cone instead of throwing the occasional wild frame.
	 */
	private static org.joml.Matrix4f applyRecoil(PoseStack pose, BakedRig rig, GunAnimations animation, float partial)
	{
		if(rig.animation == null)
			return new org.joml.Matrix4f();

		float previous = animation.lastGunRecoil;
		float recoil = previous + (animation.gunRecoil - previous) * partial;
		if(recoil <= 0F)
			return new org.joml.Matrix4f();

		// The wobble. Note this reads the FRAME's change in recoil, not the accumulated value, so
		// it only moves on the tick a shot lands and is zero once the kick has settled. The random
		// is per frame, as in the 1.20.1 build — that is what makes the shake read as a rattle
		// rather than a smooth sway.
		float result = -1.5F + SHAKE_RANDOM.nextFloat() * 3F;
		float shake = (-previous + (animation.gunRecoil - previous) * partial)
				* result * partial * rig.animation.shakeDistance;

		pose.translate(-recoil * rig.animation.recoilSlideDistance, 0F, 0F);
		var rotation=new org.joml.Matrix4f().rotateZ(FmumActionPose.rad(-recoil*rig.animation.rotateSlideDistance))
				.rotateY(FmumActionPose.rad(shake)).rotateX(FmumActionPose.rad(shake));
		pose.mulPose(rotation);
		return rotation;
	}

	/** The shake's own random, so it cannot disturb any other random sequence in the game. */
	private static final java.util.Random SHAKE_RANDOM = new java.util.Random();

	private static void applyReloadGunPose(PoseStack pose, BakedRig rig, float reloadRotate, float flip)
	{
		float r = reloadRotate;
		switch(rig.animation.animationType)
		{
			case "BOTTOM_CLIP", "PISTOL_CLIP", "SHOTGUN", "END_LOADED" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(60F * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(30F * r * flip));
				pose.translate(0.25F * r, 0F, 0F);
			}
			case "BACK_LOADED" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-75F * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-30F * r * flip));
				pose.translate(0.5F * r, 0F, 0F);
			}
			case "BULLPUP" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(70F * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(10F * r * flip));
				pose.translate(0.5F * r, -0.2F * r, 0F);
			}
			case "RIFLE" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(30F * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-30F * r * flip));
				pose.translate(0.5F * r, 0F, -0.5F * r);
			}
			case "RIFLE_TOP", "REVOLVER" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(30F * r));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(10F * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-10F * r * flip));
				pose.translate(0.1F * r, -0.2F * r, -0.1F * r);
			}
			case "ALT_PISTOL_CLIP" ->
			{
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(60F * r * flip));
				pose.translate(0.15F * r, 0.25F * r, 0F);
			}
			case "STRIKER" ->
			{
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-35F * r * flip));
				pose.translate(0.2F * r, 0F, -0.1F * r);
			}
			case "GENERIC" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45F * r));
				pose.translate(-0.2F * r, -0.5F * r, 0F);
			}
			case "CUSTOM" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rig.animation.rotateGunVertical * r));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rig.animation.rotateGunHorizontal * r));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rig.animation.tiltGun * r));
				pose.translate(rig.animation.translateGun[0] * r, rig.animation.translateGun[1] * r,
						rig.animation.translateGun[2] * r);
			}
			default -> { }
		}
	}

	/**
	 * The clip's reload path: the original's whole switch, case for case.
	 *
	 * <p>Nine cases are hand-tuned paths for particular magazine shapes (the bottom clip swinging
	 * out, the revolver's cylinder, the per-round cycle of the tube loaders), and the tenth,
	 * {@code CUSTOM}, reads the pose the model class declares. Translations are scaled by
	 * {@code scale} for the same reason as the part transforms above; rotations are not, angles
	 * being scale-free.
	 */
	private static void applyClipReload(PoseStack pose, BakedRig rig, float clipPosition,
										float loadOnlyClipPosition, float reloadRotate, float scale)
	{
		float cp = clipPosition;
		switch(rig.animation.animationType)
		{
			case "BREAK_ACTION" ->
			{
				float[] pivot = rig.animation.barrelBreakPoint;
				pose.translate(pivot[0] * scale, pivot[1] * scale, pivot[2] * scale);
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(reloadRotate * -rig.animation.breakAngle));
				pose.translate(-pivot[0] * scale, -pivot[1] * scale, -pivot[2] * scale);
				pose.translate(-cp * scale, 0F, 0F);
			}
			case "REVOLVER" ->
			{
				float[] pivot = rig.animation.revolverFlipPoint;
				pose.translate(pivot[0] * scale, pivot[1] * scale, pivot[2] * scale);
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(reloadRotate * rig.animation.revolverFlipAngle));
				pose.translate(-pivot[0] * scale, -pivot[1] * scale, -pivot[2] * scale);
				pose.translate(-cp * scale, 0F, 0F);
			}
			case "BOTTOM_CLIP" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-180F * cp));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(60F * cp));
				pose.translate(0.5F * cp * scale, 0F, 0F);
			}
			case "PISTOL_CLIP" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90F * cp * cp));
				pose.translate(0F, -1F * cp * scale, 0F);
			}
			case "ALT_PISTOL_CLIP" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(5F * cp));
				pose.translate(0F, -3F * cp * scale, 0F);
			}
			case "SIDE_CLIP" ->
			{
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F * cp));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(60F * cp));
				pose.translate(0.5F * cp * scale, 0F, 0F);
			}
			case "BULLPUP" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-150F * cp));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(60F * cp));
				pose.translate(1F * cp * scale, -0.5F * cp * scale, 0F);
			}
			case "P90" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-15F * reloadRotate * reloadRotate));
				pose.translate(0F, 0.075F * reloadRotate * scale, 0F);
				pose.translate(-2F * cp * scale, -0.3F * cp * scale, 0.5F * cp * scale);
			}
			case "RIFLE" ->
			{
				float bulletProgress = clipCycle(rig, cp);
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bulletProgress * 15F));
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bulletProgress * 15F));
				pose.translate(bulletProgress * -1F * scale, 0F, bulletProgress * 0.5F * scale);
			}
			case "RIFLE_TOP" ->
			{
				float bulletProgress = clipCycle(rig, cp);
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bulletProgress * 55F));
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bulletProgress * 95F));
				pose.translate(bulletProgress * -0.1F * scale, bulletProgress * 1F * scale,
						bulletProgress * 0.5F * scale);
			}
			case "SHOTGUN", "STRIKER" ->
			{
				float bulletProgress = clipCycle(rig, cp);
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bulletProgress * -30F));
				pose.translate(bulletProgress * -0.5F * scale, bulletProgress * -1F * scale, 0F);
			}
			case "CUSTOM" ->
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rig.animation.rotateClipVertical * cp));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rig.animation.rotateClipHorizontal * cp));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rig.animation.tiltClip * cp));
				pose.translate(rig.animation.translateClip[0] * cp * scale,
						rig.animation.translateClip[1] * cp * scale,
						rig.animation.translateClip[2] * cp * scale);
			}
			case "END_LOADED" ->
			{
				float dYaw = loadOnlyClipPosition > 0.5F ? loadOnlyClipPosition * 2F - 1F : 0F;
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45F * dYaw));
				pose.translate(-rig.animation.endLoadedAmmoDistance * dYaw * scale, -0.5F * dYaw * scale, 0F);
				float xDisplacement = loadOnlyClipPosition < 0.5F ? loadOnlyClipPosition * 2F : 1F;
				pose.translate(rig.animation.endLoadedAmmoDistance * xDisplacement * scale, 0F, 0F);
			}
			case "BACK_LOADED" ->
			{
				float dYaw = loadOnlyClipPosition > 0.5F ? loadOnlyClipPosition * 2F - 1F : 0F;
				pose.translate(rig.animation.endLoadedAmmoDistance * dYaw * scale, -0.5F * dYaw * scale, 0F);
				float xDisplacement = loadOnlyClipPosition < 0.5F ? loadOnlyClipPosition * 2F : 1F;
				pose.translate(-rig.animation.endLoadedAmmoDistance * xDisplacement * scale, 0F, 0F);
			}
			default -> { }
		}
	}

	/** One round's fraction of a tube loader's per-round cycle, exactly as the original cut it. */
	private static float clipCycle(BakedRig rig, float clipPosition)
	{
		float thing = clipPosition * rig.animation.numBulletsInReloadAnimation;
		return thing - (float)Math.floor(thing);
	}

	/**
	 * How far through its reload the gun is, as 0..1, from the original's four-phase curve.
	 *
	 * <p>{@code RenderGun} divided the reload into tilt, unload, load and untilt phases and ramped
	 * {@code reloadRotate} up over the first, held it at 1 through the middle two, and ramped it
	 * back down over the last. Anything outside those phases leaves the gun at rest.
	 */
	private static float reloadProgress(BakedRig rig, GunAnimations animation, float smoothing)
	{
		var reload=reloadFrame(smoothing);
		if(reload.step()!=com.flansmod.common.GunReloadState.Step.NONE) return reload.tilt();
		if(!animation.reloading)
			return 0F;

		// Interpolated to the frame, like everything else the original drew.
		float progress = lerp(animation.lastReloadAnimationProgress, animation.reloadAnimationProgress,
				smoothing);
		float tilt = rig.animation.tiltGunTime;
		float unload = rig.animation.unloadClipTime;
		float load = rig.animation.loadClipTime;
		float untilt = rig.animation.untiltGunTime;

		if(progress < tilt)
			return tilt <= 0F ? 1F : progress / tilt;
		if(progress < tilt + unload + load)
			return 1F;
		float remaining = progress - (tilt + unload + load);
		return untilt <= 0F ? 0F : 1F - remaining / untilt;
	}

	private static float lerp(float from, float to, float amount)
	{
		return from + (to - from) * amount;
	}

	/**
	 * The 1.7.10 per-type, per-context transform, applied in the order the old code applied it.
	 *
	 * <p>Read this against {@code RenderGun.renderGun}, {@code RenderPlane.renderItem},
	 * {@code RenderVehicle.renderItem} and {@code RenderGrenade.renderItem}: each branch below is
	 * the body of one case in those switches. The comments name which one.
	 *
	 * <p>Each of those renderers was written against the frame vanilla Minecraft put the item in,
	 * and that frame differs between 1.7.10 and 1.21:
	 * <ul>
	 *   <li>first person — both use {@code T(0.56, -0.52, -0.72)}, but 1.7.10 then applied a
	 *       45° turn and a 0.4 scale ({@code transformFirstPersonItem}, unchanged from 1.8 through
	 *       1.12). Modern versions moved those two into the item model's {@code display} block,
	 *       which this port's generated models deliberately do not carry, so they are applied
	 *       here instead — otherwise the gun is 2.5× too big and points sideways.</li>
	 *   <li>third person — modern wraps the item in an arm-local
	 *       {@code R_x(-90°) · R_y(180°) · T(±1/16, 0.125, -0.625)} that 1.7.10 did not have;
	 *       it put the item at {@code T(-1/16, 7/16, 1/16)} past the arm instead. Undoing the
	 *       modern pair and applying the old offset reproduces the old frame exactly.</li>
	 * </ul>
	 *
	 * @return true when the pose mirrors the geometry, which flips triangle winding
	 */
	private static boolean applyLegacyTransform(PoseStack pose, InfoType type, ItemDisplayContext context, BakedRig rig,
			com.flansmod.client.model.FmumRecoil.Frame recoil)
	{
		boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

		// Both versions mirror the arm-local X offset for the left hand, so every hand offset
		// below flips its sign with this. Get it wrong and the item sits on the wrong side of
		// the hand — visible, but subtle enough to miss in a screenshot.
		float handSign = leftHand ? -1F : 1F;

		float extraScale = 1F;
		boolean mirrored = false;

		if(firstPerson)
		{
			// 1.7.10 ItemRenderer: modern already supplies the hand translation.
			// Mirror the local pose for the modern left-hand context.
			if(leftHand && type instanceof GunType)
			{
				pose.scale(-1F, 1F, 1F);
				mirrored = true;
			}
			if(type instanceof GunType)
				pose.mulPose(FmumFirstPersonPose.itemFrame(new org.joml.Matrix4f()));
			else
			{
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45F));
				pose.scale(0.4F, 0.4F, 0.4F);
			}
		}
		else if(thirdPerson && type instanceof GunType)
		{
			// Rebuild the complete 1.7.10 arm-local frame, including vanilla's full-3D
			// item branch and Forge's helper=false shim, before RenderGun's EQUIPPED pose.
			boolean blocking = "block".equalsIgnoreCase(((GunType)type).itemUseAction);
			pose.mulPose(LegacyThirdPersonPose.handFrame(new org.joml.Matrix4f(), leftHand, blocking));
			mirrored = leftHand;
		}
		else if(thirdPerson)
		{
			// Non-gun held items: the 1.7.10 chain, which rebuilds the old hand transform.
			//
			// Modern's chain past the arm is  T(±1/16, 0.125, -0.625) · R_x(-90°) · R_y(180°),
			// and 1.7.10 (RenderPlayer.renderEquippedItems) put the item at T(∓1/16, 7/16, 1/16)
			// past the arm instead. The conversion is the inverse of that chain followed by the
			// old offset, so each step here is the inverse of one step there:
			//
			//   R_y(180°) · R_x(90°) · T(∓1/16, 7/16, 1/16) · T(∓1/16, -0.125, 0.625)
			//
			// The order matters and is easy to get wrong: the translation must be applied BEFORE
			// the rotations in code (they compose right-to-left), because the inverse of
			// "rotate then translate" is "un-translate then un-rotate". Applying a merged
			// translation after the rotations leaves the offset in the wrong frame and the gun
			// ends up beside the player instead of in the hand.
			pose.translate(-handSign * 0.0625F, -0.125F, 0.625F);
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90F));
			pose.translate(-handSign * 0.0625F, 0.4375F, 0.0625F);

			// Then the transformation vanilla Minecraft itself applied to the item before the
			// mod's renderer was reached. RenderPlayer picked between two of these by asking the
			// item's {@code isFull3D()}, and the Flan items split both ways: ItemGrenade overrides
			// it to true, while ItemVehicle and ItemPlane inherit the default false.
			if(type instanceof GrenadeType)
			{
				// isFull3D() == true. Note the mirrored Y in the scale, which is how vanilla
				// turns the model to face down the arm; the RenderGrenade branch below applies its
				// own Z mirror, and the two together leave the winding positive.
				pose.translate(0F, 0.1875F, 0F);
				pose.scale(0.625F, -0.625F, 0.625F);
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-100F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45F));
			}
			else
			{
				// isFull3D() == false: the plain-item branch, which is where vehicles and planes
				// are drawn from.
				pose.translate(0.25F, 0.1875F, -0.1875F);
				pose.scale(0.375F, 0.375F, 0.375F);
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(60F));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(20F));
			}
		}
		else
		{
			// Dropped items (GROUND). Modern already spins the stack with the entity's age, so
			// only the old placement offset is added; a second spin here would double it.
			if(!(type instanceof GunType || type instanceof GrenadeType
					|| type instanceof PlaneType || type instanceof VehicleType))
				return mirrored;
			pose.translate(-0.2F, 0.2F, 0.1F);
			return mirrored;
		}

		if(type instanceof GunType)
		{
			if(thirdPerson)
			{
				pose.mulPose(LegacyThirdPersonPose.placement(new org.joml.Matrix4f(),
						rig.thirdPersonOffsetX, rig.thirdPersonOffsetY, rig.thirdPersonOffsetZ));
			}
			else if(firstPerson)
			{
				float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
				var action=actionFrame(partial);
				float ads = FmumActionPose.ads(rig.animation,action,AimState.blend(partial));
				GunAnimations animation = GunAnimationRegistry.right(Minecraft.getInstance().player);
				float reload = rig.animation == null ? 0F : reloadProgress(rig, animation, partial);
				// FMUM uses the pack's sprint/reload vectors, in the same blend as holding/ADS.
				pose.mulPose(FmumFirstPersonPose.placement(new org.joml.Matrix4f(), rig.animation,
						ads, SprintStance.blend(partial), reload,action,
						com.flansmod.client.ClientGunMotion.frame(rig.animation,partial,ads,SprintStance.blend(partial)),recoil));
			}
			else
			{
				// RenderGun, case ENTITY. Modern already spins the stack with the entity's age, so
				// only the model's own frame offset is added here.
				pose.translate(-0.2F + rig.itemFrameOffsetX, 0.2F + rig.itemFrameOffsetY,
						0.1F + rig.itemFrameOffsetZ);
				return mirrored;
			}
		}
		else if(type instanceof GrenadeType)
		{
			if(thirdPerson)
			{
				// RenderGrenade, case EQUIPPED.
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(35F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-5F));
				pose.translate(0.75F, -0.22F, -0.08F);
				pose.translate(0F, 0.25F, 0F);
			}
			else if(firstPerson)
			{
				// RenderGrenade, case EQUIPPED_FIRST_PERSON.
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(25F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-5F));
				pose.translate(0.15F, 0.2F, -0.6F);
				pose.translate(0F, 0.25F, 0F);
			}
		}
		else if(type instanceof PlaneType planeType)
		{
			// RenderPlane.renderItem: a base scale per context, then scale / cameraDistance.
			// Both the base and the camera distance are 1.7.10's own numbers.
			float base = firstPerson ? 0.5F : thirdPerson ? 1F : 1.5F;
			if(thirdPerson)
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(15F));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(15F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90F));
				pose.translate(0F, 0.2F, 0.4F);
			}
			else if(firstPerson)
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(25F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-5F));
				pose.translate(0.15F, 0.45F, -0.6F);
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
			}
			extraScale = base / cameraDistanceOf(planeType) / safeModelScale(type);
		}
		else if(type instanceof VehicleType vehicleType)
		{
			// RenderVehicle.renderItem: like the plane, but rotated 270 about Y and offset
			// slightly differently in third person.
			float base = firstPerson ? 0.5F : thirdPerson ? 1F : 1.5F;
			if(thirdPerson)
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(15F));
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(15F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270F));
				pose.translate(0F, 0.15F, -0.4F);
			}
			else if(firstPerson)
			{
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(25F));
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-5F));
				pose.translate(0.15F, 0.45F, -0.6F);
			}
			extraScale = base / cameraDistanceOf(vehicleType) / safeModelScale(type);
		}

		if(extraScale != 1F)
			pose.scale(extraScale, extraScale, extraScale);

		return mirrored;
	}

	/**
	 * The scale baked into the rig is {@code modelScale / 16}, because that is what a gun's
	 * renderer applied. Planes and vehicles never applied {@code modelScale} — they divided the
	 * raw geometry by their camera distance instead — so the extra factor divides it back out
	 * before applying 1.7.10's own ratio.
	 */
	private static float safeModelScale(InfoType type)
	{
		return type.modelScale <= 0F ? 1F : type.modelScale;
	}

	private static float cameraDistanceOf(DriveableType type)
	{
		return type.cameraDistance <= 0F ? 5F : type.cameraDistance;
	}

	/** The pack's skin for this definition, or the missing-texture marker. */
	private static ResourceLocation textureFor(InfoType type)
	{
		// The rule lives in one place because the model baker needs the same answer: it corrects a
		// model's UVs against the texture that will actually be bound, and correcting against a
		// different file would move them off the mark. See Textures.
		ResourceLocation resolved = Textures.forType(type);
		return resolved != null ? resolved
				: ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
	}

	/** Draws the generated flat icon through the standard GUI item path. */
	private static void renderIcon(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
								   int light, int overlay)
	{
		Item item = stack.getItem();

		// The item's own "#inventory" model is builtin/entity — that is why this renderer is
		// running at all — so the icon is fetched from the separate standalone model the
		// generated assets ship. Asking the shaper for the item's model here would hand back the
		// same builtin/entity model and recurse straight back into this method.
		String path = item instanceof ItemFlan flan
				? com.flansmod.common.ItemAssets.registryPathOf(flan.type)
				: null;
		ResourceLocation iconId = path == null ? null
				: ResourceLocation.fromNamespaceAndPath(FlansMod.MODID,
						com.flansmod.common.GeneratedAssets.iconModelPath(path));

		BakedModel icon = iconId == null ? null
				: Minecraft.getInstance().getModelManager()
						.getModel(ModelCache.iconModelLocation(iconId));
		if(icon == null)
			return;

		var itemRenderer = Minecraft.getInstance().getItemRenderer();
		// The outer render already applied the half-block centring shift that item rendering
		// relies on; the call below applies its own, so one of them is undone first.
		pose.translate(0.5F, 0.5F, 0.5F);
		itemRenderer.render(stack, ItemDisplayContext.GUI, false, pose, buffers, light, overlay, icon);
	}
}
