package party.lemons.dyeablecompasses.forge;

import dev.architectury.platform.Platform;
import dev.architectury.platform.forge.EventBuses;
import dev.architectury.utils.Env;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import party.lemons.dyeablecompasses.DyeableCompasses;
import party.lemons.dyeablecompasses.DyeableCompassesClient;

@Mod(DyeableCompasses.MOD_ID)
public class DyeableCompassesForge
{
	public DyeableCompassesForge() {
		EventBuses.registerModEventBus(DyeableCompasses.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
		DyeableCompasses.init();

		if(Platform.getEnvironment() == Env.CLIENT)
			DyeableCompassesClient.initClient();
	}
}
