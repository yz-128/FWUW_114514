import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.model.loader.*;
import com.flansmod.common.types.*;
import org.joml.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** CPU evidence of the same GUI frame/studio colours, not an in-game capture. */
public final class RenderGunModificationView {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        var pack=new OfflinePack(root);pack.readTypes();
        for(String name:List.of("VPO101","BocchiTheGlock18C")) {
            var gun=(GunType)pack.types.stream().filter(t->name.equals(t.shortName)).findFirst().orElseThrow();
            var parts=new ArrayList<FPPreview.Part>();
            parts.add(new FPPreview.Part(new Matrix4f(),ModelCache.rigFor(gun,gun.modelScale),FPPreview.skin(root,gun.texture),-1));
            for(var group:gun.defaultAttachments)FPPreview.addNodes(parts,root,gun,group,new Matrix4f(),0);
            var corners=new ArrayList<Vector3f>();for(var part:parts)VerifyGunModificationView.collect(corners,part.rig(),part.m());
            int width=1000,height=430;var frame=GunModificationView.frame(corners,-24,0,1F,10,34,width-20,height-60);
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
            graphics.setColor(new Color(0x15283A));graphics.fillRect(0,0,width,height);
            graphics.setColor(new Color(0x203C50));for(int x=20;x<width;x+=40)graphics.drawLine(x,35,x,height-25);for(int y=35;y<height-20;y+=40)graphics.drawLine(10,y,width-10,y);
            graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,14));graphics.setColor(new Color(0x5DE0C2));graphics.drawString(name+" · 修正后的自动取景与界面光照",14,23);graphics.dispose();
            float[] depth=new float[width*height];Arrays.fill(depth,-Float.MAX_VALUE);
            for(var part:parts)for(var quad:part.rig().quads) {
                var transform=new Matrix4f(frame.matrix()).mul(part.m());var normalMatrix=new Matrix3f();transform.normal(normalMatrix);
                float shade=GunModificationView.shade(normalMatrix.transform(new Vector3f(quad.nx(),quad.ny(),quad.nz())));
                var p=new Vector3f[4];for(int i=0;i<4;i++)p[i]=transform.transformPosition(new Vector3f(quad.positions()[i]));
                RenderGunModificationLayout.triangle(image,depth,p[0],p[1],p[2],quad.uvs()[0],quad.uvs()[1],quad.uvs()[2],part.tex(),shade);
                RenderGunModificationLayout.triangle(image,depth,p[0],p[2],p[3],quad.uvs()[0],quad.uvs()[2],quad.uvs()[3],part.tex(),shade);
            }
            graphics=image.createGraphics();graphics.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));graphics.setColor(new Color(0x849BAE));graphics.drawString("离线模型预览；与游戏共用取景/明暗计算，未执行实际 OpenGL 显示。",14,height-8);graphics.dispose();
            ImageIO.write(image,"png",out.resolve(name+"-preview.png").toFile());
        }
    }
}
