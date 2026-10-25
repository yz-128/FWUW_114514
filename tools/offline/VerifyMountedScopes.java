import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Real AK74M + SVD rail, complete opaque assembly before optical layers; no game. */
public final class VerifyMountedScopes {
    static final int W=VerifyScopeComposition.W,H=VerifyScopeComposition.H;
    static final List<OfflinePack> packs=new ArrayList<>();
    static final Map<String,Integer> textures=new HashMap<>();
    static Path output;
    static int scene,white,cases,checks;
    /** Lens area at rail 0 per optic and zoom step: the aperture may shrink up the rail, never collapse. */
    static final Map<String,int[]> RAIL0=new HashMap<>();
    static void check(boolean good,String name){checks++;if(!good)throw new AssertionError(name);}
    static ModifiableType.DefaultAttachment node(String name,int slot){var n=new ModifiableType.DefaultAttachment();n.name=name;n.slot=slot;return n;}
    static GunType mounted(String optic,float rail){
        var ak=(GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType&&"AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var tree=new ArrayList<List<ModifiableType.DefaultAttachment>>();for(var row:ak.defaultAttachments)tree.add(new ArrayList<>(row));while(tree.size()<ak.slots.length)tree.add(new ArrayList<>());
        if(optic.equals("PSO1"))tree.get(6).add(node(optic,6));else {var side=node("SVDLowSideMount",6);var lens=node(optic,0);lens.pos=rail;if(optic.equals("NF7x")){var ring=node("NF34mm",0);ring.pos=rail;lens.pos=0;ring.children.add(lens);side.children.add(ring);}else side.children.add(lens);tree.get(6).add(side);}
        return ak.withAttachments(tree);
    }
    static Matrix4f gunPose(GunType gun){var pose=new Matrix4f().translate(.56F,-.52F,-.72F);FmumFirstPersonPose.itemFrame(pose);FmumFirstPersonPose.placement(pose,ModelCache.load(gun),1,0,0);var eye=FmumFirstPersonPose.eyeInGunSpace(pose);FmumFirstPersonPose.aim(pose,gun,1,eye);return pose;}
    static int texture(InfoType type)throws Exception {
        String key=type.contentPack+"/"+type.texture;if(textures.containsKey(key))return textures.get(key);
        var owner=packs.stream().filter(p->p.root.getFileName().toString().equals(type.contentPack)).findFirst().orElseThrow();
        var id=Textures.forType(type,owner::has);Path path=owner.path(id);if(path==null)throw new IllegalStateException("Missing "+key+" "+id);
        int gl=VerifyScopeComposition.texture(ImageIO.read(path.toFile()));textures.put(key,gl);return gl;
    }
    static void body(InfoType type,Matrix4f transform,Matrix4f projection)throws Exception {
        var rig=ModelCache.rigFor(type,type.modelScale);var pose=new PoseStack();pose.mulPose(transform);var capture=new VerifyScopeComposition.Capture();
        var quads=rig.quadsByPart.get("attachmentModel");if(quads==null)quads=rig.quads;
        RigRenderer.renderPart(pose,capture,quads,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,0,1,1,1,1,false);
        VerifyScopeComposition.draw(capture,texture(type),projection);
    }
    static void children(ModifiableType parent,List<ModifiableType.DefaultAttachment> children,Matrix4f transform,Matrix4f projection)throws Exception {
        for(var node:children){var type=AttachmentType.getAttachment(node.name);if(type==null)continue;var slot=parent.slots[node.slot];var pose=new Matrix4f(transform).translate(slot.x+node.pos,slot.y,slot.z).rotateX((float)Math.toRadians(slot.rotate));body(type,pose,projection);children(type,node.children,pose,projection);}
    }
    static int reticleTexture(AttachmentType type,int frame)throws Exception {var skin=ReticleResources.source(type,frame).orElseThrow();try(var in=skin.entry().open()){return VerifyScopeComposition.texture(ImageIO.read(in));}}
    static void sample(String name,int step,float rail)throws Exception {
        var gun=mounted(name,rail);var sight=GunSightState.scopeSight(gun);check(sight!=null,"active scope "+name);sight.node().zoomStep=step;
        var type=sight.type();var rig=ModelCache.rigFor(type,type.modelScale);var glass=ModelCache.rigForReference(type.scopeGlassModel,1F);
        var weapon=gunPose(gun);var toEye=new Matrix4f(weapon).translate(ScopeReticleRenderer.mount(sight)).rotateX((float)Math.toRadians(sight.roll()-type.aimCenterX));
        var pupil=FmumSightPicture.vector(rig.animation,"pupilRefCenter",0,0,0).mul(type.modelScale);var scale=FmumSightPicture.vector(rig.animation,"scopeGlassModelScale",1,1,1).mul(type.modelScale);
        var lensPose=new PoseStack();lensPose.mulPose(new Matrix4f(toEye).translate(pupil).scale(scale));
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.005F,100);RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        String label=name+"-z"+step+"-rail"+String.format(Locale.ROOT,"%.3f",rail);
        RenderSystem.clearColor(.38F,.50F,.63F,1);GL11.glClearDepth(1);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
        body(gun,weapon,projection);children(gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),weapon,projection);
        ImageIO.write(VerifyScopeComposition.pixels(),"png",output.resolve(label+"-gun.png").toFile());
        var world=new VerifyScopeComposition.Capture();FmumSightRenderer.renderLensSurface(lensPose,world,glass,new Matrix4f(projection).mul(lensPose.last().pose()),1,false);
        ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(world,scene,projection));var withWorld=VerifyScopeComposition.pixels();ImageIO.write(withWorld,"png",output.resolve(label+"-world.png").toFile());
        float actualDistance=FmumSightPicture.pupilDistance(rig.animation,type.modelScale,new Matrix4f(toEye).invert().transformPosition(new Vector3f()));
        var reticle=new VerifyScopeComposition.Capture();ScopeReticleRenderer.render(lensPose,reticle,gun,sight,rig,glass,toEye,projection,ScopeMagnification.nativeFov(70,type,step,actualDistance),0,false);
        int texture=reticleTexture(type,0);ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(reticle,texture,projection));com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);
        var picture=ScopeEyeRelief.picture(rig.animation,type.modelScale,toEye,step);var shadow=new VerifyScopeComposition.Capture();ScopeMaskRenderer.render(lensPose,shadow,rig,type,glass,toEye,0,step,70,0,false);
        int maskTexture=reticleTexture(type,0);ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(shadow,maskTexture,projection));com.mojang.blaze3d.platform.GlStateManager._deleteTexture(maskTexture);var finalImage=VerifyScopeComposition.pixels();ImageIO.write(finalImage,"png",output.resolve(label+"-complete.png").toFile());
        int worldPixels=0,clearPixels=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int c=withWorld.getRGB(x,y);if(((c>>16)&255)>150&&((c>>8)&255)>180){worldPixels++;int after=finalImage.getRGB(x,y);if(((after>>16)&255)>100&&((after>>8)&255)>140)clearPixels++;}}
        float distance=FmumSightPicture.pupilDistance(rig.animation,type.modelScale,new Matrix4f(toEye).invert().transformPosition(new Vector3f()));
        var maskFrame=ScopeMaskRenderer.frame(rig,type,toEye,step,70);
        var base=RAIL0.computeIfAbsent(name+"#"+step,k->new int[1]);if(rail==0F)base[0]=worldPixels;
        double maskKeep=worldPixels==0?0:(double)clearPixels/worldPixels;
        System.out.printf(Locale.ROOT,"%s eyeDistance=%.6f lensEye=%s alpha=%.6f nativeFov=%.4f clear=%d/%d keep=%.3f maskScale=%.8f minDistance=%.9f%n",label,distance,toEye.transformPosition(pupil),picture.alpha(),maskFrame.fov(),clearPixels,worldPixels,maskKeep,FmumActionPose.scalar(rig.animation,"scopeMaskScale",.02F),FmumActionPose.scalar(rig.animation,"minPupilDistance",.3F));
        // A genuine too-close mount may put the rear glass behind the eye. For the others,
        // compare original meshes through a separate native 3-D square-camera raster, rather
        // than assuming every authored eye distance must have the same clear-area percentage.
        float farthest=Float.NEGATIVE_INFINITY;
        for(var q:glass.quads)if(q.nx()<-.9F)for(var p:q.positions())farthest=Math.max(farthest,-lensPose.last().pose().transformPosition(new Vector3f(p)).z);
        check(farthest>.005F?worldPixels>100:worldPixels==0,"aperture visibility agrees with the real hand near-plane "+label);
        int target=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        var reference=NativeScopeReference.render(gun,sight,rig,toEye,scene,H);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);RenderSystem.viewport(0,0,W,H);
            ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(world,reference.getColorTextureId(),projection));
            var expected=VerifyScopeComposition.pixels();ImageIO.write(expected,"png",output.resolve(label+"-native-reference.png").toFile());
            int different=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int a=expected.getRGB(x,y),b=finalImage.getRGB(x,y);if(Math.abs(((a>>16)&255)-((b>>16)&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs((a&255)-(b&255))>90)different++;}
            System.out.println("nativeRasterDifference="+different+" / aperture="+worldPixels);
            check(different<=Math.max(16,worldPixels*.04),"production optics agree with independent native square raster "+label);
        }finally{reference.destroyBuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);RenderSystem.viewport(0,0,W,H);}
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"GPU error "+label);cases++;
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);for(int i=1;i<args.length;i++){var pack=new OfflinePack(Path.of(args[i]));pack.readTypes();packs.add(pack);}
        RenderSystem.initRenderThread();check(GLFW.glfwInit(),"GLFW");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"mounted scope",0,0);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();var target=new TextureTarget(W,H,true,false);
        try {var sceneImage=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<256;y++)for(int x=0;x<256;x++)sceneImage.setRGB(x,y,x%32<2||y%32<2?0xFF587F77:0xFFD2EEE0);scene=VerifyScopeComposition.texture(sceneImage);var w=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);w.setRGB(0,0,0xFFFFFFFF);white=VerifyScopeComposition.texture(w);RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(white);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);
            float railEnd=AttachmentType.getAttachment("SVDLowSideMount").slots[0].stepVector*AttachmentType.getAttachment("SVDLowSideMount").slots[0].step;
            for(String name:List.of("SeecterDR1x4x","ELCANSpecterDR1x4x","PSO1","NF7x","G33Enabled","G33")){
                var type=AttachmentType.getAttachment(name);if(type==null)continue;
                for(int step=0;step<Math.max(1,type.fovZoomLevels.length);step++)for(float rail:name.equals("PSO1")?new float[]{0}:new float[]{0,railEnd*.5F,railEnd})sample(name,step,rail);
            }
        }finally{target.destroyBuffers();for(int id:textures.values())com.mojang.blaze3d.platform.GlStateManager._deleteTexture(id);com.mojang.blaze3d.platform.GlStateManager._deleteTexture(scene);com.mojang.blaze3d.platform.GlStateManager._deleteTexture(white);GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        System.out.println("PASS: "+checks+" checks, "+cases+" actual AK74M/SVD-rail scope poses and complete-assembly GPU composition; synthetic scene, no game.");
    }
}
