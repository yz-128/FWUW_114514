import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Original opaque scope tube and original recessed rear glass rasterized together.
 * No screenshots, displaced test planes or coordinate-only acceptance checks.
 * Args: output EFT-pack Octagon-pack [--diagnose]. --diagnose records old failures.
 */
public final class VerifyScopeDepthOcclusion {
    static final int W=VerifyScopeComposition.W,H=VerifyScopeComposition.H;
    static final List<OfflinePack> packs=new ArrayList<>();
    static Path output;
    static int white,scene,checks,cases,failures;
    static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    static int bodyTexture(AttachmentType type)throws Exception {
        var owner=packs.stream().filter(p->p.root.getFileName().toString().equals(type.contentPack)).findFirst().orElseThrow();
        return VerifyScopeComposition.texture(ImageIO.read(owner.path(Textures.forType(type,owner::has)).toFile()));
    }
    static void reset(){
        RenderSystem.clearColor(.38F,.50F,.63F,1);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);GL11.glClearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
    }
    static void body(VerifyScopeComposition.Capture mesh,int texture,Matrix4f projection){
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);VerifyScopeComposition.draw(mesh,texture,projection);
    }
    static float[] depth(){
        var pixels=BufferUtils.createFloatBuffer(W*H);GL11.glReadPixels(0,0,W,H,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,pixels);
        var result=new float[W*H];pixels.get(result);return result;
    }
    static void sample(String name,float yaw,float pitch)throws Exception {
        var type=AttachmentType.getAttachment(name);check(type!=null,"fixture optic "+name);
        var rig=ModelCache.rigFor(type,type.modelScale);
        var glass=ModelCache.rigForReference(type.scopeGlassModel,1F);
        check(glass!=null&&!glass.quads.isEmpty(),"real authored glass "+name);
        var centre=FmumSightPicture.vector(rig.animation,"pupilRefCenter",0,0,0).mul(type.modelScale);
        var scale=FmumSightPicture.vector(rig.animation,"scopeGlassModelScale",1,1,1).mul(type.modelScale);
        // Rotate the unmodified complete optic about the centre of its authored rear pupil.
        var toEye=new Matrix4f().translate(0,0,-.32F).rotateY((float)Math.toRadians(yaw))
                .rotateX((float)Math.toRadians(pitch)).rotateY((float)(Math.PI/2)).translate(new Vector3f(centre).negate());
        var projection=new Matrix4f().perspective((float)Math.toRadians(60),W/(float)H,.005F,100);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        var bodyPose=new PoseStack();bodyPose.mulPose(toEye);
        var bodyMesh=new VerifyScopeComposition.Capture();
        var quads=rig.quadsByPart.get("attachmentModel");if(quads==null)quads=rig.quads;
        RigRenderer.renderPart(bodyPose,bodyMesh,quads,LightTexture.FULL_BRIGHT,0,1,1,1,1,false);
        var glassPose=new PoseStack();glassPose.mulPose(new Matrix4f(toEye).translate(centre).scale(scale));
        var glassMesh=new VerifyScopeComposition.Capture();
        RigRenderer.renderPart(glassPose,glassMesh,glass.quads.stream().filter(q->q.nx()<-.9F).toList(),LightTexture.FULL_BRIGHT,0,0,0,0,1,false);
        check(!bodyMesh.vertices.isEmpty()&&!glassMesh.vertices.isEmpty(),"actual model vertices emitted "+name);
        int texture=bodyTexture(type);
        String label=name+"-yaw"+String.format(Locale.ROOT,"%+.0f",yaw)+"-pitch"+String.format(Locale.ROOT,"%+.0f",pitch);
        try {
            reset();body(bodyMesh,texture,projection);var tube=VerifyScopeComposition.pixels();
            ImageIO.write(tube,"png",output.resolve(label+"-tube.png").toFile());
            // Reference reproduces native opaque geometry depth: the rear pupil cannot
            // paint over closer tube walls, lip bevels or the near edge of the rim.
            RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
            VerifyScopeComposition.draw(glassMesh,white,projection);var nativeImage=VerifyScopeComposition.pixels();
            ImageIO.write(nativeImage,"png",output.resolve(label+"-native-depth.png").toFile());
            reset();body(bodyMesh,texture,projection);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            VerifyScopeComposition.draw(glassMesh,white,projection);var old=VerifyScopeComposition.pixels();
            ImageIO.write(old,"png",output.resolve(label+"-old-no-depth.png").toFile());
            reset();body(bodyMesh,texture,projection);
            var beforeDepth=depth();int beforeFunction=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(glassMesh,white,projection));
            var production=VerifyScopeComposition.pixels();
            check(Arrays.equals(beforeDepth,depth()),"optical composite preserves original tube depth buffer "+label);
            check(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==beforeFunction,"production restores depth function "+label);
            ImageIO.write(production,"png",output.resolve(label+"-production.png").toFile());
            int reproduced=0,mismatch=0,visibleGlass=0;
            var difference=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int expected=nativeImage.getRGB(x,y)&0xFFFFFF,prior=old.getRGB(x,y)&0xFFFFFF,actual=production.getRGB(x,y)&0xFFFFFF;
                if(expected!=prior){reproduced++;difference.setRGB(x,y,0xFF2C4C);}
                if(expected!=actual)mismatch++;
                if(expected==0&&(tube.getRGB(x,y)&0xFFFFFF)!=0)visibleGlass++;
            }
            ImageIO.write(difference,"png",output.resolve(label+"-occluded-rim-difference.png").toFile());
            System.out.printf(Locale.ROOT,"%s realQuads=%d glassQuads=%d nativeVisibleGlass=%d oldOcclusionError=%d productionMismatch=%d%n",label,quads.size(),glassMesh.vertices.size()/4,visibleGlass,reproduced,mismatch);
            check(reproduced>20,"fixture reproduces disabled-depth overdraw of real tube walls "+label);
            check(visibleGlass>100,"reference retains real recessed glass "+label);
            if(mismatch>0)failures++;
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"production restores depth state "+label);
            var activeGlass=new VerifyScopeComposition.Capture();
            FmumSightRenderer.renderLensSurface(glassPose,activeGlass,glass,new Matrix4f(projection).mul(glassPose.last().pose()),1,false);
            reset();body(bodyMesh,texture,projection);RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
            VerifyScopeComposition.draw(activeGlass,scene,projection);var activeReference=VerifyScopeComposition.pixels();
            ImageIO.write(activeReference,"png",output.resolve(label+"-active-native-depth.png").toFile());
            reset();body(bodyMesh,texture,projection);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            VerifyScopeComposition.draw(activeGlass,scene,projection);var activeOld=VerifyScopeComposition.pixels();
            ImageIO.write(activeOld,"png",output.resolve(label+"-active-old-no-depth.png").toFile());
            reset();body(bodyMesh,texture,projection);
            var activeDepth=depth();ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(activeGlass,scene,projection));
            var activeProduction=VerifyScopeComposition.pixels();
            ImageIO.write(activeProduction,"png",output.resolve(label+"-active-production.png").toFile());
            int activeError=0,activeOldError=0,visibleWorld=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                int expected=activeReference.getRGB(x,y)&0xFFFFFF;
                if(expected!=(activeProduction.getRGB(x,y)&0xFFFFFF))activeError++;
                if(expected!=(activeOld.getRGB(x,y)&0xFFFFFF))activeOldError++;
                if(((expected>>16)&255)>150&&((expected>>8)&255)>170)visibleWorld++;
            }
            System.out.printf(Locale.ROOT,"%s activeWorldPixels=%d oldActiveOcclusionError=%d productionActiveMismatch=%d%n",label,visibleWorld,activeOldError,activeError);
            check(visibleWorld>100,"depth-tested original rear glass retains the active scope scene "+label);
            check(activeOldError>20,"active scene fixture reproduces closer tube-wall overdraw "+label);
            check(Arrays.equals(activeDepth,depth()),"active scope scene preserves original tube depth "+label);
            if(activeError>0)failures++;
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"GL error "+label);cases+=2;
        }finally{com.mojang.blaze3d.platform.GlStateManager._deleteTexture(texture);}
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);
        boolean diagnose=false;for(int i=1;i<args.length;i++){if(args[i].equals("--diagnose")){diagnose=true;continue;}var pack=new OfflinePack(Path.of(args[i]));pack.readTypes();packs.add(pack);}
        RenderSystem.initRenderThread();check(GLFW.glfwInit(),"GLFW");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"real recessed optic depth",0,0);check(window!=0,"invisible GL context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();var target=new TextureTarget(W,H,true,false);
        try {
            var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0xFFFFFFFF);white=VerifyScopeComposition.texture(image);
            var world=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<256;y++)for(int x=0;x<256;x++)world.setRGB(x,y,x%32<2||y%32<2?0xFF789C9A:0xFFD2EEE0);scene=VerifyScopeComposition.texture(world);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(white);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);
            for(String name:List.of("G33Enabled","SeecterDR1x4x","ELCANSpecterDR1x4x"))for(float yaw:new float[]{-25,25})sample(name,yaw,10);
        }finally{target.destroyBuffers();com.mojang.blaze3d.platform.GlStateManager._deleteTexture(white);com.mojang.blaze3d.platform.GlStateManager._deleteTexture(scene);GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        System.out.println("Depth comparison: "+checks+" checks, "+cases+" real tube/glass GPU cases, "+failures+" production regressions. Synthetic scene; no game.");
        if(!diagnose)check(failures==0,"production glass must respect actual closer tube geometry; failing cases="+failures);
    }
}
