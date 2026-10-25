package com.flansmod.client.render;

import com.flansmod.client.model.FmumCasing;
import com.flansmod.common.FmumSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;

/** Local physics owns the sound: no early clink on ejection, no frame-driven repetition. */
public final class CasingLandingSounds {
    private CasingLandingSounds() { }
    public static void play(FmumCasing casing,FmumCasing.Landing hit) {
        var mc=Minecraft.getInstance();if(mc.level==null||hit==null)return;
        // FMUM had no casing audio, so this is judgement rather than transcription: a contact
        // under the water line is the one place the metal clink reads as wrong. It is decided
        // before the three-sound quota is spent, so a wet bounce does not silence dry ones.
        if(FmumCasing.submerged(mc.level,hit.point())||!casing.audibleLanding(hit))return;
        var sound=mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.parse("flansmod:sounds/casing1.ogg")).isPresent()
                ?FmumSounds.event("casing1",10):SoundEvents.CHAIN_HIT;
        float softness=hit.surface().is(BlockTags.WOOL)||hit.surface().is(BlockTags.DIRT)||hit.surface().is(BlockTags.SAND)?.3F:1F;
        float volume=Math.clamp(hit.speed()*2F,.08F,.5F)*softness;
        float pitch=.92F+(float)((casing.age*31L+casing.lifetime)%19)/100F;
        mc.level.playLocalSound(hit.point().x,hit.point().y,hit.point().z,sound,SoundSource.PLAYERS,volume,pitch,false);
    }
}
