import com.flansmod.client.SightFoldAnimations;
import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.FmumActionPose;
import com.flansmod.client.render.TurborigItemRenderer;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Measures what the mounted-attachment renderer emits for a real AN/PEQ-15, laser off and on. */
public final class InspectAnpeq
{
	static final class Recorder implements MultiBufferSource, VertexConsumer
	{
		final List<float[]> positions = new ArrayList<>();
		public VertexConsumer getBuffer(RenderType type) { return this; }
		public VertexConsumer addVertex(float x, float y, float z) { positions.add(new float[] {x, y, z}); return this; }
		public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
		public VertexConsumer setUv(float u, float v) { return this; }
		public VertexConsumer setUv1(int u, int v) { return this; }
		public VertexConsumer setUv2(int u, int v) { return this; }
		public VertexConsumer setNormal(float x, float y, float z) { return this; }
		public VertexConsumer setLight(int light) { return this; }
		float maxAbs() { float max = 0F; for(var p : positions) for(var v : p) max = Math.max(max, Math.abs(v)); return max; }
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
		var root = Path.of(args[0]);
		for(var dir : (Iterable<Path>)Files.list(root).sorted()::iterator)
		{
			if(!Files.exists(dir.resolve("pack.mcmeta"))) continue;
			try { new OfflinePack(dir).readTypes(); } catch(RuntimeException ignored) { }
		}
		var at = AttachmentType.getAttachment("ANPEQ15");
		System.out.println("ANPEQ15 laserFrom=" + Arrays.toString(FmumActionPose.vector(
				ModelCache.rigFor(at, at.modelScale).animation, "laserFrom")) + " modelScale=" + at.modelScale);
		for(boolean laser : new boolean[] { false, true })
		{
			var gunFile = new TypeFile(EnumType.gun, "AnpeqHost", false);
			gunFile.lines.addAll(List.of("ShortName AnpeqHost", "AttachPoints 0 0 0", "AttachVectors 1 10",
					"MaxAttachments 1"));
			var gun = new GunType(gunFile);
			var slot = new ModifiableType.AttachableSlot();
			slot.x = .3F; slot.y = .2F; slot.rotate = 90;
			gun.slots = new ModifiableType.AttachableSlot[] { slot };
			var node = new ModifiableType.DefaultAttachment();
			node.name = at.shortName; node.slot = 0;
			gun.defaultAttachments = List.of(List.of(node));
			new ItemFlan(gun, new net.minecraft.world.item.Item.Properties());
			var stack = new ItemStack(gun.item);
			GunAttachments.setTacticalState(stack, false, laser);
			var view = GunAttachments.view(gun, stack);
			var method = Arrays.stream(TurborigItemRenderer.class.getDeclaredMethods())
					.filter(c -> c.getName().equals("renderAttachments")).findFirst().orElseThrow();
			method.setAccessible(true);
			var recorder = new Recorder();
			method.invoke(null, new PoseStack(), recorder, view,
					view.defaultAttachments.stream().flatMap(List::stream).toList(),
					false, 0, 0, null, SightFoldAnimations.Frame.IDLE, new int[0], 0, stack, true);
			System.out.printf("laserOn=%s vertices=%d maxAbs=%.3f%n", laser, recorder.positions.size(), recorder.maxAbs());
			int shown = 0;
			for(var p : recorder.positions)
				if(Math.abs(p[0]) > 1F && shown++ < 6)
					System.out.printf("  far vertex (%.3f, %.3f, %.3f)%n", p[0], p[1], p[2]);
		}
	}
}
