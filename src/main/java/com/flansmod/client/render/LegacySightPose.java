package com.flansmod.client.render;

import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ModifiableType;
import java.util.ArrayList;
import java.util.List;

/** FMUM 1.7.10 default sight group, resolved from the same tree rendered on the gun. */
public final class LegacySightPose
{
	private LegacySightPose() { }

	public record Center(String source, float roll, float x, float y, float z) { }

	public static Center forGun(GunType gun)
	{
		List<Center> sights = new ArrayList<>();
		// recordSights visits slots/parts backwards. setSightsAtForGun selects group 0,
		// then puts the sight furthest along X first within that same Y/Z sight line.
		for(int i = gun.defaultAttachments.size(); --i >= 0; )
			collect(gun, gun.defaultAttachments.get(i), 0F, 0F, 0F, 0F, sights, 0);
		if(sights.isEmpty())
			return new Center(gun.shortName, gun.aimCenterX, 0F, gun.aimCenterY, gun.aimCenterZ);
		Center first = sights.get(0), selected = first;
		for(Center sight : sights)
			if(sight.y == first.y && sight.z == first.z && sight.x > selected.x)
				selected = sight;
		return selected;
	}

	private static void collect(ModifiableType owner, List<ModifiableType.DefaultAttachment> nodes,
			float x, float y, float z, float roll, List<Center> sights, int depth)
	{
		if(depth > 8) return; // Same bound as the offline assembly walker.
		float sin = (float)Math.sin(Math.toRadians(roll)), cos = (float)Math.cos(Math.toRadians(roll));
		for(int i = nodes.size(); --i >= 0; )
		{
			ModifiableType.DefaultAttachment node = nodes.get(i);
			AttachmentType type = AttachmentType.getAttachment(node.name);
			if(type == null || node.slot < 0 || node.slot >= owner.slots.length) continue;
			ModifiableType.AttachableSlot slot = owner.slots[node.slot];
			float nx = x + slot.x + node.pos;
			float ny = y + slot.y * cos - slot.z * sin;
			float nz = z + slot.y * sin + slot.z * cos;
			float nr = roll + slot.rotate;
			if("sight".equalsIgnoreCase(type.itsType))
			{
				float rs = (float)Math.sin(Math.toRadians(nr)), rc = (float)Math.cos(Math.toRadians(nr));
				sights.add(new Center(type.shortName, nr, nx,
						ny + type.aimCenterY * rc - type.aimCenterZ * rs,
						nz + type.aimCenterY * rs + type.aimCenterZ * rc));
			}
			collect(type, node.children, nx, ny, nz, nr, sights, depth + 1);
		}
	}
}
