import com.flansmod.client.SightFoldAnimations;
import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.TurborigItemRenderer;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Proves the real mounted-attachment render path emits the legacy glow lightmap sections.
 *
 * <p>GPT's emissive verifier tested the leaf renderer and the GUI; this one drives
 * {@code TurborigItemRenderer.renderAttachments} itself with a real pack attachment mounted on a
 * fixture gun and records the actual per-vertex lightmap values, so the claim "glow materials are
 * fixed in game" is backed by the same traversal the held gun uses.
 *
 * <p>Args: {@code <pack root>}.
 */
public final class VerifyMountedEmission
{
	static int checks;
	static void check(boolean ok, String message) { checks++; if(!ok) throw new AssertionError(message); }

	static final class Recorder implements MultiBufferSource, VertexConsumer
	{
		final List<Integer> lights = new ArrayList<>();
		int vertices;
		public VertexConsumer getBuffer(RenderType type) { return this; }
		public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
		public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
		public VertexConsumer setUv(float u, float v) { return this; }
		public VertexConsumer setUv1(int u, int v) { return this; }
		public VertexConsumer setUv2(int u, int v) { return this; }
		public VertexConsumer setNormal(float x, float y, float z) { return this; }
		public VertexConsumer setLight(int light) { lights.add(light); return this; }
	}

	public static void main(String[] args) throws Exception
	{
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		((net.minecraft.core.MappedRegistry<?>)net.minecraft.core.registries.BuiltInRegistries.ITEM).unfreeze();
		var root = Path.of(args[0]);
		var pack = new OfflinePack(root.resolve("EscapeFromTarcraftReforged_1.5.3"));
		pack.readTypes();

		// Find real authored glow attachments (the pack defines them; nothing is synthesised).
		var glow = new ArrayList<AttachmentType>();
		for(var type : pack.types)
			if(type instanceof AttachmentType at)
			{
				var rig = ModelCache.rigFor(at, at.modelScale);
				if(rig != null && rig.hasEmissiveProfile())
					glow.add(at);
			}
		check(glow.size() >= 10, "the pack has real authored glow attachments: " + glow.size());

		int verified = 0;
		for(var at : glow)
		{
			var rig = ModelCache.rigFor(at, at.modelScale);
			int bodyQuads = rig.attachmentBody(0).size();
			int lightQuads = rig.emissivePasses(0).stream().mapToInt(pass -> pass.quads().size()).sum();
			if(lightQuads == 0)
				continue;

			// Fixture gun carrying exactly this attachment, then walk the same renderer the held gun uses.
			var gunFile = new TypeFile(EnumType.gun, "EmissionMount", false);
			gunFile.lines.addAll(List.of("ShortName EmissionMount", "AttachPoints 0 0 0",
					"AttachVectors 1 10", "MaxAttachments 1"));
			var gun = new GunType(gunFile);
			gun.read(gunFile);
			var slot = new ModifiableType.AttachableSlot();
			slot.x = .3F; slot.y = .2F; slot.rotate = 90;
			gun.slots = new ModifiableType.AttachableSlot[] { slot };
			var node = new ModifiableType.DefaultAttachment();
			node.name = at.shortName; node.slot = 0;
			gun.defaultAttachments = List.of(List.of(node));
			new com.flansmod.common.types.ItemFlan(gun, new net.minecraft.world.item.Item.Properties());
			var stack = new ItemStack(gun.item);
			var view = GunAttachments.view(gun, stack);

			var method = Arrays.stream(TurborigItemRenderer.class.getDeclaredMethods())
					.filter(candidate -> candidate.getName().equals("renderAttachments"))
					.findFirst().orElseThrow(() -> new AssertionError("renderAttachments is missing"));
			method.setAccessible(true);
			var recorder = new Recorder();
			var invokeArgs = new Object[] { new PoseStack(), recorder, view,
					view.defaultAttachments.stream().flatMap(List::stream).toList(),
					false, 0, 0, null, SightFoldAnimations.Frame.IDLE, new int[0], 0, stack, true };
			method.invoke(null, invokeArgs);

			int bodyVertices = bodyQuads * 4, glowVertices = lightQuads * 4;
			check(recorder.vertices == bodyVertices + glowVertices,
					"mounted renderer emits exactly body+glow vertices for " + at.shortName
							+ ": " + recorder.vertices + " vs " + (bodyVertices + glowVertices));
			for(int i = 0; i < bodyVertices; i++)
				check(recorder.lights.get(i) == 0, "body keeps the incoming dark lightmap: " + at.shortName);
			int emissiveVertices = 0;
			for(int i = bodyVertices; i < recorder.lights.size(); i++)
			{
				check(recorder.lights.get(i) > 0, "glow vertices are lifted out of the dark: " + at.shortName);
				emissiveVertices++;
			}
			check(emissiveVertices == glowVertices, "all glow quads reach the vertex stream: " + at.shortName);
			verified++;
		}
		check(verified >= 10, "mounted traversal verified on real glow attachments: " + verified);
		System.out.println("PASS VerifyMountedEmission: " + checks + " checks; "
				+ verified + " mounted glow attachments emit body at the world lightmap and every glow quad "
				+ "at a lifted/full-bright lightmap through the real held-gun attachment renderer. No game/GPU.");
	}
}
