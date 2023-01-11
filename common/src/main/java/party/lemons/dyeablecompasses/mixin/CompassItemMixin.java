package party.lemons.dyeablecompasses.mixin;

import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompassItem.class)
public class CompassItemMixin
{
	@Inject(at = @At("HEAD"), method = "isFoil", cancellable = true)
	public void isFoil(ItemStack stack, CallbackInfoReturnable<Boolean> cbi)
	{
		cbi.setReturnValue(false);
	}
}
