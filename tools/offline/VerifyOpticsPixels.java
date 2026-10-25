import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Final native-shader GPU pixels for each optical definition/frame/zoom/hand.
 * Normalized optical bench, plus separately labelled real AK74M poses; no Minecraft is started. */
public final class VerifyOpticsPixels {
    static final int W=VerifyScopeComposition.W,H=VerifyScopeComposition.H;
    static final List<OfflinePack> packs=new ArrayList<>();
    static final Map<ResourceLocation,Integer> textures=new HashMap<>();
    static final List<BufferedImage> tiles=new ArrayList<>();
    static final List<String> findings=new ArrayList<>(),rows=new ArrayList<>();
    static int checks,cases,definitions,scene,white;
    static Path output;
    static void check(boolean value,String message){checks++;if(!value){findings.add(message);System.out.println("FAIL "+message);}}
    static boolean present(String ref){return ref!=null&&!ref.isBlank()&&!ref.equalsIgnoreCase("none");}
    static Path path(ResourceLocation id){return packs.stream().map(p->p.path(id)).filter(Objects::nonNull).findFirst().orElseThrow(()->new IllegalStateException("Missing "+id));}
    static ResourceLocation skin(AttachmentType at,int frame){return Textures.forReticle(at,frame,id->packs.stream().anyMatch(p->p.has(id)));}
    static BufferedImage reticleImage(AttachmentType at,int frame){try{var source=ReticleResources.source(at,frame);if(source.isPresent())try(var in=source.get().entry().open()){return ImageIO.read(in);}return ImageIO.read(path(skin(at,frame)).toFile());}catch(Exception e){throw new IllegalStateException(e);}}
    static int texture(AttachmentType at,int frame){return textures.computeIfAbsent(Textures.forReticle(at,frame),key->VerifyScopeComposition.texture(reticleImage(at,frame)));}
    static boolean nonempty(AttachmentType at,int frame){var image=reticleImage(at,frame);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>25)return true;return false;}
    record Layer(VerifyScopeComposition.Capture capture,int texture) { }
    static final class Layers implements MultiBufferSource {
        final List<Layer> layers=new ArrayList<>();
        final Map<RenderType,Integer> ids;
        Layers(GunType view){ids=new HashMap<>();for(var sight:GunSightState.selectedGroup(view).sights())if(present(sight.type().lightStuffModel)) {
            var id=skin(sight.type(),sight.node().dotFrame);int gl=texture(sight.type(),sight.node().dotFrame);
            ids.put(ScopeRenderLayers.overlay(id),gl);ids.put(RenderType.entityTranslucentEmissive(id),gl);
            // With no Minecraft resource manager, production emission uses its final fallback
            // identifier. Bind the actual pack PNG to that identifier in this offline fixture.
            var fallback=Textures.forReticle(sight.type(),sight.node().dotFrame);
            ids.put(ScopeRenderLayers.overlay(fallback),gl);ids.put(RenderType.entityTranslucentEmissive(fallback),gl);
        }}
        public VertexConsumer getBuffer(RenderType type){var c=new VerifyScopeComposition.Capture();Integer gl=ids.get(type);if(gl==null)throw new IllegalStateException("Unexpected layer "+type);layers.add(new Layer(c,gl));return c;}
        void draw(Matrix4f projection){for(var layer:layers)VerifyScopeComposition.draw(layer.capture,layer.texture,projection);}
        int vertices(){return layers.stream().mapToInt(l->l.capture.vertices.size()).sum();}
    }
    static ModifiableType.DefaultAttachment node(AttachmentType at,int slot){var n=new ModifiableType.DefaultAttachment();n.name=at.shortName;n.slot=slot;return n;}
    static GunType fixture(AttachmentType at,boolean magnifier){
        var f=new TypeFile(EnumType.gun,"PixelBench_"+at.shortName,false);var gun=new GunType(f);
        gun.slots=new ModifiableType.AttachableSlot[magnifier?2:1];Arrays.setAll(gun.slots,i->new ModifiableType.AttachableSlot());
        if(magnifier){var dot=AttachmentType.getAttachment("CobraEKPrefiexSightPic");if(dot==null)dot=AttachmentType.getAttachment("EOTechEXPS30");
            gun.slots[0].x=.5F;gun.slots[0].y=at.aimCenterY-dot.aimCenterY;
            gun.defaultAttachments=List.of(List.of(node(dot,0)),List.of(node(at,1)));
        }else gun.defaultAttachments=List.of(List.of(node(at,0)));
        return gun;
    }
    static void clear(){RenderSystem.clearColor(.1F,.15F,.2F,1);GL11.glClearDepth(1);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);}
    static BufferedImage image(){return VerifyScopeComposition.pixels();}
    static boolean changed(int a,int b){return Math.abs((a&255)-(b&255))+Math.abs(((a>>8)&255)-((b>>8)&255))+Math.abs(((a>>16)&255)-((b>>16)&255))>15;}
    static void tile(String label,BufferedImage image){var tile=new BufferedImage(384,240,BufferedImage.TYPE_INT_RGB);var g=tile.createGraphics();g.drawImage(image,0,24,384,216,null);g.setColor(Color.WHITE);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));g.drawString(label,6,16);g.dispose();tiles.add(tile);}
    static void audit(AttachmentType at,int frame,int step,boolean mirrored)throws Exception {
        boolean scope=present(at.scopeGlassModel),dot=present(at.lightStuffModel);var view=fixture(at,scope&&!dot);
        var own=view.defaultAttachments.getLast().getFirst();own.dotFrame=frame;own.zoomStep=step;
        var rig=ModelCache.rigFor(at,at.modelScale);var model=rig.animation;
        var center=FmumSightPicture.aimCenter(model,at.aimCenterY,at.aimCenterZ);
        var glass=scope?ModelCache.rigForReference(at.scopeGlassModel,1F):null;
        // Keep every authored ocular safely in front of the hand near plane. This fixture
        // tests optical composition independently of a particular gun's rail placement.
        var pupil=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(at.modelScale);
        var scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(at.modelScale);
        float eyeX=scope?pupil.x+glass.minX*scale.x-.22F:-.5F;
        var eye=new Vector3f(eyeX,center.y,center.z);
        var toEye=new Matrix4f().rotationY((float)Math.PI/2).translate(new Vector3f(eye).negate());
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),W/(float)H,.05F,100);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        var pose=new PoseStack();pose.mulPose(toEye);
        var label=at.shortName+" f"+frame+" z"+step+(mirrored?" L":" R");
        var layers=new Layers(view);BufferedImage background,withReticle,complete;
        clear();
        if(scope){
            var selected=GunSightState.scopeSight(view);check(selected!=null&&selected.type()==at,label+" selected scope");
            if(!dot)check(GunSightState.selectedGroup(view).sights().size()==2,label+" coaxial group");
            pose.translate(pupil.x,pupil.y,pupil.z);pose.scale(scale.x,scale.y,scale.z);
            var world=new VerifyScopeComposition.Capture();FmumSightRenderer.renderLensSurface(pose,world,glass,new Matrix4f(projection).mul(pose.last().pose()),1,mirrored);
            check(!world.vertices.isEmpty(),label+" front aperture geometry");
            ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(world,scene,projection));background=image();
            double fov=ScopeMagnification.nativeFov(70,at,step,FmumSightPicture.pupilDistance(model,at.modelScale,eye));
            ScopeReticleRenderer.render(pose,layers,view,selected,rig,glass,toEye,projection,fov,0,mirrored);
            ScopeRenderLayers.composite(()->layers.draw(projection));withReticle=image();
            var shadow=new VerifyScopeComposition.Capture();ScopeMaskRenderer.render(pose,shadow,rig,at,glass,toEye,frame,step,70,0,mirrored);
            int maskTexture=texture(at,frame);ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(shadow,maskTexture,projection));complete=image();
        }else {
            // A solid pale background makes a missing direct dot measurable as unchanged pixels.
            RenderSystem.clearColor(.82F,.93F,.88F,1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,false);background=image();
            FmumSightRenderer.renderDot(pose,layers,at,own,rig,eye,FmumSightPicture.pupilDistance(model,at.modelScale,eye),1,0,mirrored);
            ScopeRenderLayers.composite(()->layers.draw(projection));withReticle=image();complete=withReticle;
        }
        check(layers.vertices()>0,label+" reticle geometry");
        int changed=0,inside=0,dark=0,centerInside=0,centerDark=0,finalClear=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            int base=background.getRGB(x,y),after=withReticle.getRGB(x,y);
            if(changed(base,after))changed++;
            if(((base>>8)&255)>120){inside++;boolean black=((after>>16)&255)<50&&((after>>8)&255)<50&&(after&255)<50;if(black)dark++;
                int finished=complete.getRGB(x,y);if(((finished>>8)&255)>80)finalClear++;
                if(Math.abs(x-W/2)<60&&Math.abs(y-H/2)<45){centerInside++;if(black)centerDark++;}}
        }
        boolean expected=dot?nonempty(at,frame):true;
        if(expected&&changed==0)for(var layer:layers.layers){float minX=1E9F,minY=1E9F,maxX=-1E9F,maxY=-1E9F;for(var v:layer.capture.vertices){var p=projection.transform(new org.joml.Vector4f(v[0],v[1],v[2],1));float x=p.x/p.w*W/2,y=p.y/p.w*H/2;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}System.out.println("FOOTPRINT "+label+" "+(maxX-minX)+"x"+(maxY-minY)+" pixels");}
        check(expected?changed>0:changed==0,label+(expected?" visible reticle pixels":" authored off frame remains transparent"));
        if(scope){check(inside>1500,label+" visible world pixels");check(dark<inside*.4,label+" outer mask leaves a clear aperture");
            if(centerInside>1000)check(centerDark<centerInside*.4,label+" no opaque central block");
            check(complete.getRGB(5,5)==background.getRGB(5,5),label+" no leakage outside the aperture");}
        if(scope)check(finalClear>inside*.01,label+" eye-relief mask leaves visible scene pixels");
        check(GL11.glGetError()==GL11.GL_NO_ERROR,label+" GPU errors");
        rows.add(String.join(",",at.contentPack,at.shortName,Integer.toString(frame),Integer.toString(step),Boolean.toString(mirrored),Integer.toString(changed),Integer.toString(inside),Integer.toString(dark),Integer.toString(centerInside),Integer.toString(centerDark),Integer.toString(finalClear)));
        if(!mirrored){tile(label,complete);if(frame==0&&step==0)ImageIO.write(complete,"png",output.resolve(at.shortName+".png").toFile());}
        cases++;
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);
        for(int i=1;i<args.length;i++){var pack=new OfflinePack(Path.of(args[i]));pack.readTypes();packs.add(pack);}
        var optics=AttachmentType.attachments.stream().filter(a->"sight".equalsIgnoreCase(a.itsType)&&!a.isFoldedVariant()&&(present(a.scopeGlassModel)||present(a.lightStuffModel))).toList();
        RenderSystem.initRenderThread();if(!GLFW.glfwInit())throw new IllegalStateException("GLFW init");GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"offline all optics",0,0);if(window==0)throw new IllegalStateException("GLFW context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();
        var target=new TextureTarget(W,H,true,false);
        try {
            var sceneImage=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<256;y++)for(int x=0;x<256;x++)sceneImage.setRGB(x,y,x%16==0||y%16==0?0xFF789C9A:0xFFD2EEE0);scene=VerifyScopeComposition.texture(sceneImage);
            var whiteImage=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);whiteImage.setRGB(0,0,0xFFFFFFFF);white=VerifyScopeComposition.texture(whiteImage);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(white);RenderSystem.activeTexture(GL13.GL_TEXTURE0);target.bindWrite(true);
            for(var at:optics){for(int frame=0;frame<Math.max(1,at.lightTextureCount);frame++)for(int step=0;step<Math.max(1,at.fovZoomLevels.length);step++)for(boolean mirrored:new boolean[]{false,true})audit(at,frame,step,mirrored);definitions++;System.out.println("CHECKED "+at.contentPack+"/"+at.shortName);}
            int cols=4;var sheet=new BufferedImage(cols*384,((tiles.size()+cols-1)/cols)*240,BufferedImage.TYPE_INT_RGB);var g=sheet.createGraphics();for(int i=0;i<tiles.size();i++)g.drawImage(tiles.get(i),(i%cols)*384,(i/cols)*240,null);g.dispose();ImageIO.write(sheet,"png",output.resolve("all-optics.png").toFile());
            rows.addFirst("pack,optic,frame,zoomStep,mirrored,reticlePixels,worldPixels,blackPixels,centralPixels,centralBlackPixels,finalClearPixels");Files.write(output.resolve("pixels.csv"),rows);Files.write(output.resolve("findings.txt"),findings);
        } finally {target.destroyBuffers();for(int id:textures.values())GL11.glDeleteTextures(id);GL11.glDeleteTextures(scene);GL11.glDeleteTextures(white);GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        if(!findings.isEmpty())throw new AssertionError(findings.size()+" GPU pixel findings; see "+output);
        System.out.println("PASS: "+checks+" assertions, "+definitions+" optical definitions, "+cases+" final GPU pixel cases; original models/textures/vanilla shaders. Normalized optical bench, not a game screenshot or all gun mounts.");
    }
}
