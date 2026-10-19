import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3f;

/** Read every installed optical definition using production readers; no game or OpenGL. */
public final class InspectAllSights {
    public static void main(String[] args) throws Exception {
        for(String arg:args)new OfflinePack(Path.of(arg)).readTypes();
        for(var at:AttachmentType.attachments) {
            if(!"sight".equalsIgnoreCase(at.itsType))continue;
            var model=ModelCache.load(at);var rig=ModelCache.rigFor(at,at.modelScale);
            if(model==null){System.out.println("MISSING "+at.contentPack+"/"+at.shortName+" "+at.modelString);continue;}
            float[][] border=model.fmumVectorArrays.get("reticleBorder"), far=model.fmumVectorArrays.get("reticleAt");
            var eye=new Vector3f(-.5F,at.aimCenterY,at.aimCenterZ);
            int hits=0;if(border!=null && far!=null)for(var p:far)
                if(p!=null && FmumSightPicture.intersection(eye,new Vector3f(p),border,FmumSightPicture.vector(model,"normalVecOfDotBorder",1,0,0))!=null)hits++;
            System.out.printf(Locale.ROOT,"%s/%s folded=%s aim=(%.5f,%.5f) border=%d far=%d axisHits=%d dot=%s frames=%d zoom=%s glass=%s%n",
                at.contentPack,at.shortName,at.isFoldedVariant(),at.aimCenterY,at.aimCenterZ,border==null?0:border.length,far==null?0:far.length,hits,at.lightStuffModel,at.lightTextureCount,Arrays.toString(at.fovZoomLevels),at.scopeGlassModel);
            if(at.scopeGlassModel!=null){
                var glass=ModelCache.rigForReference(at.scopeGlassModel,1F);
                var center=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(at.modelScale);
                var scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(at.modelScale);
                System.out.printf(Locale.ROOT,"  lens center=%s scale=%s bounds=%s objective=%s reticleScale=%s%n",center,scale,
                    glass==null?"MISSING":String.format(Locale.ROOT,"X[%.4f,%.4f] Y[%.4f,%.4f] Z[%.4f,%.4f]",glass.minX,glass.maxX,glass.minY,glass.maxY,glass.minZ,glass.maxZ),
                    FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0),model.fmumScalars.get("reticleScale"));
            }
        }
    }
}
