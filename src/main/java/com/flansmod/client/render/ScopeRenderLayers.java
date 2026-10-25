package com.flansmod.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Housing occlusion for opaque scope glass and historical transparent lens diagnostics. */
public final class ScopeRenderLayers extends RenderStateShard {
    private ScopeRenderLayers(){super("flansmod_scope_layers",()->{},()->{});}
    private static final Map<ResourceLocation,RenderType> OVERLAYS=new ConcurrentHashMap<>();
    private static final Map<ResourceLocation,RenderType> BLACK_GLASS=new ConcurrentHashMap<>();
    public static RenderType blackGlass(ResourceLocation texture) {
        return BLACK_GLASS.computeIfAbsent(texture,key->RenderType.create("flansmod_scope_black_glass_"+key,
                DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,false,
                RenderType.CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(key,false,false)).setOverlayState(OVERLAY)
                        .setCullState(CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false)));
    }
    public static RenderType overlay(ResourceLocation texture) {
        return OVERLAYS.computeIfAbsent(texture,key->RenderType.create("flansmod_scope_overlay_"+key,
                DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,false,
                RenderType.CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(key,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOverlayState(OVERLAY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
    /** Native FMUM glass inherits housing occlusion; transparent layers must not write depth. */
    public static void composite(Runnable draw) {
        withDepth(draw,false);
    }
    /** The completed IMAGE is opaque. Its authored multi-face glass must self-occlude, just
     * as RenderGun:1935-1943 does with the normal item depth-write state still enabled. */
    public static void glass(Runnable draw) {
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {RenderSystem.enableCull();withDepth(draw,true);}
        finally {if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();}
    }
    private static void withDepth(Runnable draw,boolean writeGlass) {
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int function=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        try {RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(writeGlass);draw.run();}
        finally {RenderSystem.depthFunc(function);RenderSystem.depthMask(write);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();}
    }
}
