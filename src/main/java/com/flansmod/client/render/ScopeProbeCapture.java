package com.flansmod.client.render;

import com.flansmod.client.AimState;
import com.flansmod.common.FlansMod;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.TextureUtil;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Same-frame staged scope diagnostics, in the order the layers are actually composed:
 * world-only, world+reticle, and the final glass. FMUM 1.7.10's probes only captured a single
 * combined image, which made it impossible to tell a black nested-world render from a black
 * reticle atlas or a black final glass.
 *
 * <p>Enabled by default; disable with {@code -Dflansmod.scopeProbes=false}. One sample per optic
 * and stage per client process, taken only at full ADS.
 */
public final class ScopeProbeCapture
{
	private static final Set<String> CAPTURED=new LinkedHashSet<>();
	private static Path directory;
	private static String version="dev";
	private static boolean runWritten;
	private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HHmmss");

	private ScopeProbeCapture() { }

	public static boolean enabled() {
		return Boolean.parseBoolean(System.getProperty("flansmod.scopeProbes","true"));
	}

	private static Path directory(Minecraft mc) {
		if(directory!=null)return directory;
		version=net.neoforged.fml.ModList.get().getModContainerById(FlansMod.MODID)
				.map(container->container.getModInfo().getVersion().toString()).orElse("dev");
		directory=mc.gameDirectory.toPath().resolve("flansmod-diagnostics")
				.resolve("optics-"+version+"-"+LocalTime.now().format(TIME));
		try {Files.createDirectories(directory);} catch(Exception ignored) { }
		return directory;
	}

	/** Captures one scopeTarget color texture stage at full ADS, before it is copied/consumed. */
	public static void stage(Minecraft mc,String scope,AttachmentType type,GunType view,int step,
			String stage,int textureId,int width,int height,double imageFov,double renderFov) {
		if(!enabled() || mc==null || scope==null || width<1 || height<1)return;
		if(AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true))<.99F)return;
		var currentSight=GunSightState.scopeSight(view);
		int dot=currentSight==null?-1:currentSight.node().dotFrame;
		String key=scope+"|"+step+"|"+dot+"|"+stage;
		if(!CAPTURED.add(key))return;
		var dir=directory(mc);
		String name=scope.toLowerCase(java.util.Locale.ROOT)+"-z"+step+"-f"+dot+"-"+stage;
		try {
			TextureUtil.writeAsPNG(dir,name,textureId,0,width,height);
			if(!runWritten)writeRun(mc,dir);
			var sight=GunSightState.scopeSight(view);
			String details="version="+version
					+"\nrunDirectory="+dir.toAbsolutePath()
					+"\nstage="+stage
					+"\nscope="+scope
					+"\nzoomStep="+step
					+"\ndotFrame="+(sight==null?-1:sight.node().dotFrame)
					+"\ng33Enabled="+("G33Enabled".equals(scope))
					+"\nimageSize="+width+"x"+height
					+"\nimageTexture="+textureId
					+"\nimageFov="+imageFov
					+"\nrenderFov="+renderFov
					+"\nreadFramebuffer="+GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING)
					+"\ndrawFramebuffer="+GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)
					+"\nshaderPack="+ShaderMuzzleRenderer.shadersEnabled()
					+"\nfabulous="+Minecraft.useShaderTransparency()
					+"\ngpu="+GL11.glGetString(GL11.GL_RENDERER)
					+"\nglVersion="+GL11.glGetString(GL11.GL_VERSION)
					+"\n";
			Files.writeString(dir.resolve(name+".txt"),details,StandardCharsets.UTF_8);
			FlansMod.LOGGER.info("[ScopeProbe] stage {} for {} saved to {}",stage,scope,dir.toAbsolutePath());
		} catch(Exception error) {
			FlansMod.LOGGER.warn("Could not save scope probe stage {} ({})",stage,scope,error);
		}
	}

	/** Final composed hand-pass image, read from the main target after the lens batch was flushed. */
	public static void finalGlass(Minecraft mc,String scope) {
		finalGlass(mc,scope,0,0);
	}
	public static void finalGlass(Minecraft mc,String scope,int step,int dot) {
		if(!enabled() || mc==null || scope==null)return;
		if(AimState.blend(mc.getTimer().getGameTimeDeltaPartialTick(true))<.99F)return;
		String key=scope+"|"+step+"|"+dot+"|6-final-glass";
		if(!CAPTURED.add(key))return;
		var dir=directory(mc);
		var target=mc.getMainRenderTarget();
		String name=scope.toLowerCase(java.util.Locale.ROOT)+"-z"+step+"-f"+dot+"-6-final-glass";
		try {
			TextureUtil.writeAsPNG(dir,name,target.getColorTextureId(),0,target.width,target.height);
			if(!runWritten)writeRun(mc,dir);
			Files.writeString(dir.resolve(name+".txt"),
					"version="+version+"\nrunDirectory="+dir.toAbsolutePath()
							+"\nstage=6-final-glass\nscope="+scope+"\nzoomStep="+step+"\ndotFrame="+dot
							+"\nsize="+target.width+"x"+target.height
							+"\ntexture="+target.getColorTextureId()+"\n",
					StandardCharsets.UTF_8);
			FlansMod.LOGGER.info("[ScopeProbe] final glass for {} saved to {}",scope,dir.toAbsolutePath());
		} catch(Exception error) {
			FlansMod.LOGGER.warn("Could not save final scope glass probe ({})",scope,error);
		}
	}

	private static String classSha256(String resource) {
		try(var in=ScopeProbeCapture.class.getResourceAsStream(resource)) {
			if(in==null)return "missing";
			var digest=java.security.MessageDigest.getInstance("SHA-256");
			var buffer=new byte[8192];int read;
			while((read=in.read(buffer))>0)digest.update(buffer,0,read);
			var text=new StringBuilder();
			for(var value:digest.digest())text.append(String.format("%02x",value));
			return text.toString();
		} catch(Exception error) {
			return "error:"+error.getClass().getSimpleName();
		}
	}

	private static void writeRun(Minecraft mc,Path dir) {
		runWritten=true;
		try {
			var source=FlansMod.class.getProtectionDomain().getCodeSource().getLocation();
			var location=Path.of(source.toURI());
			String modified=Files.getLastModifiedTime(location).toString();
			String details="version="+version
					+"\nmodId="+FlansMod.MODID
					+"\nclassSource="+location
					+"\nclassModified="+modified
					+"\nscopeReticleClassSha256="+classSha256("/com/flansmod/client/render/ScopeReticleRenderer.class")
					+"\nscopeOverlayClassSha256="+classSha256("/com/flansmod/client/render/ScopeFramebufferOverlays.class")
					+"\nscopeImageViewClassSha256="+classSha256("/com/flansmod/client/render/ScopeFramebufferOverlays$ImageView.class")
					+"\nscopeProjectionClassSha256="+classSha256("/com/flansmod/client/render/LegacyScopeProjection.class")
					+"\nscopeMaskClassSha256="+classSha256("/com/flansmod/client/render/ScopeMaskRenderer.class")
					+"\nscopeWorldClassSha256="+classSha256("/com/flansmod/client/render/ScopeWorldTexture.class")
					+"\nclientSide="+mc.isLocalServer()
					+"\nrunStarted="+java.time.LocalDateTime.now()
					+"\nnote=stages are written from the same frame in composition order; the capture "
					+"happens after each upload so texture reads see the completed layer"
					+"\n";
			Files.writeString(dir.resolve("run.txt"),details,StandardCharsets.UTF_8);
		} catch(Exception ignored) { }
	}
}
