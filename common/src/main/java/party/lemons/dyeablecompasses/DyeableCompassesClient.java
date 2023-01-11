package party.lemons.dyeablecompasses;

import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CompassItem;

public class DyeableCompassesClient
{
	public static void initClient()
	{
		DyeableCompasses.DYED_COMPASS.listen(i-> ItemPropertiesRegistry.register(i, new ResourceLocation("angle"), new CompassItemPropertyFunction((arg, arg2, arg3) ->
				CompassItem.isLodestoneCompass(arg2) ? CompassItem.getLodestonePosition(arg2.getOrCreateTag()) : CompassItem.getSpawnPosition(arg))));


		ColorHandlerRegistry.registerItemColors((argx, i) -> i <= 0 ? -1 : ((DyeableCompasses.Dyeable)argx.getItem()).getColor(argx), DyeableCompasses.DYED_COMPASS);
	}
}
