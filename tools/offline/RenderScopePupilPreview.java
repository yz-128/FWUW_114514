import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.*;
import com.flansmod.common.types.AttachmentType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** CPU diagnostic of the production eye-box function, with a labelled synthetic grid. Not a game capture. */
public final class RenderScopePupilPreview {
    public static void main(String[] args)throws Exception {
        new OfflinePack(Path.of(args[0])).readTypes();
        var type=AttachmentType.getAttachment("PSO1");var model=ModelCache.load(type);
        var objective=FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0);
        var eye=new Vector3f(objective.x-.5F,objective.y,objective.z);
        var aligned=new Matrix4f().rotationY((float)Math.PI/2).translate(new Vector3f(eye).negate());
        var shifted=new Matrix4f(aligned);shifted.m31(shifted.m31()+.025F);
        var far=new Matrix4f().rotationY((float)Math.PI/2).translate(-objective.x+2,-objective.y,-objective.z);
        var pictures=new ScopeEyeRelief.Picture[]{ScopeEyeRelief.picture(model,type.modelScale,aligned,0),
                ScopeEyeRelief.picture(model,type.modelScale,shifted,0),ScopeEyeRelief.picture(model,type.modelScale,far,0)};
        var image=new BufferedImage(1080,420,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        graphics.setColor(new Color(11,17,26));graphics.fillRect(0,0,1080,420);
        graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,17));graphics.setColor(Color.WHITE);
        graphics.drawString("PSO-1 / production pupil shading / CPU diagnostic, not an in-game screenshot",20,28);
        String[] labels={"Aligned eye","Eye moved up 0.025 blocks","Eye too far"};
        for(int tile=0;tile<3;tile++) {
            var picture=pictures[tile];
            for(int y=0;y<300;y++)for(int x=0;x<300;x++) {
                float ny=(150-y)/150F,nz=(x-150)/150F;
                if(ny*ny+nz*nz>1)continue;
                float shade=picture.shadow(ny,nz),gain=picture.alpha()*(1-shade);
                int grid=(x%30<2 || y%30<2)?160:235;
                if(Math.abs(x-150)<1 || Math.abs(y-150)<1)grid=30;
                int value=Math.clamp((int)(grid*gain),0,255);
                image.setRGB(30+tile*360+x,65+y,(value<<16)|(value<<8)|value);
            }
            graphics.setColor(Color.WHITE);graphics.drawString(labels[tile],30+tile*360,394);
        }
        graphics.dispose();Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(image,"png",output.toFile());
        System.out.println(output.toAbsolutePath());
    }
}
