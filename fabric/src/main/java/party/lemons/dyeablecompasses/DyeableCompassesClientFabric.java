package party.lemons.dyeablecompasses;

import net.fabricmc.api.ClientModInitializer;

public class DyeableCompassesClientFabric implements ClientModInitializer
{

	@Override
	public void onInitializeClient()
	{
		DyeableCompassesClient.initClient();
	}
}
