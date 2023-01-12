package party.lemons.dyeablecompasses;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DyeableCompasses {
    public static final String MOD_ID = "dyeablecompasses";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(MOD_ID, Registries.RECIPE_SERIALIZER);
    public static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(MOD_ID, Registries.RECIPE_TYPE);

    public static final RegistrySupplier<Item> DYED_COMPASS = ITEMS.register("dyed_compass", () -> new DyeableCompass(new Item.Properties()));
    public static final RegistrySupplier<RecipeSerializer<DyeRecipe>> DYE = RECIPE_SERIALIZERS.register(new ResourceLocation(MOD_ID, "compass_dye"), ()->new SimpleCraftingRecipeSerializer<>(DyeRecipe::new));
    public static final RegistrySupplier<RecipeType<DyeRecipe>> DYE_RECIPE = RECIPES.register(new ResourceLocation(MOD_ID, "compass_dye"), ()->new RecipeType<>() {
        public String toString() {
            return MOD_ID + ":compass_dye";
        }
    });

    public static void init() {
        ITEMS.register();
        RECIPE_SERIALIZERS.register();
        RECIPES.register();

        DYED_COMPASS.listen(i->{
            CauldronInteraction.WATER.put(i, (blockState, level, blockPos, player, interactionHand, itemStack) -> {
                Item item = itemStack.getItem();
                if (!(item instanceof Dyeable dyeable)) {
                    return InteractionResult.PASS;
                } else if (!dyeable.hasCustomColor(itemStack)) {
                    return InteractionResult.PASS;
                } else {
                    if (!level.isClientSide) {
                        player.awardStat(Stats.CLEAN_ARMOR);
                        ItemStack basicCompass = new ItemStack(Items.COMPASS, itemStack.getCount());
                        if(itemStack.getTag() != null)
                            basicCompass.setTag(itemStack.getTag());

                        player.setItemInHand(interactionHand, basicCompass);

                        dyeable.clearColor(itemStack);
                        LayeredCauldronBlock.lowerFillLevel(blockState, level, blockPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            });
        });

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(Commands.literal("exdata").executes(c->{
                Registry<ConfiguredFeature<?, ?>> cfg =  c.getSource().getLevel().registryAccess().registry(Registries.CONFIGURED_FEATURE).get();
                Registry<PlacedFeature> placed =  c.getSource().getLevel().registryAccess().registry(Registries.PLACED_FEATURE).get();

               cfg.forEach(f->{
                   ResourceLocation location = cfg.getKey(f);
                    var json = ConfiguredFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, f).get().left().get();
                   var file = Platform.getConfigFolder().resolve("data")
                           .resolve(location.getNamespace())
                           .resolve("configured_features")
                           .resolve(location.getPath() + ".json");
                    writeDirect(file, json);

                });

                placed.forEach(f->{
                    ResourceLocation location = placed.getKey(f);
                    var json = PlacedFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, f).get().left().get();
                    var file = Platform.getConfigFolder().resolve("data")
                            .resolve(location.getNamespace())
                            .resolve("placed_features")
                            .resolve(location.getPath() + ".json");
                    writeDirect(file, json);

                });

                return 1;
            }));
        });
    }

    protected static void writeDirect(Path path, JsonElement json) {
        var parent = path.getParent();
        if (!Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        try (var out = Files.newBufferedWriter(path)) {
            out.write(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class DyeableCompass extends CompassItem implements Dyeable
    {
        private static final Logger LOGGER = LogUtils.getLogger();

        public DyeableCompass(Properties properties)
        {
            super(properties);
        }

        @Override
        public boolean isFoil(ItemStack itemStack)
        {
            return false;
        }

        public InteractionResult useOn(UseOnContext useOnContext) {
            BlockPos blockPos = useOnContext.getClickedPos();
            Level level = useOnContext.getLevel();
            if (!level.getBlockState(blockPos).is(Blocks.LODESTONE)) {
                return super.useOn(useOnContext);
            } else {
                level.playSound(null, blockPos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
                Player player = useOnContext.getPlayer();
                ItemStack itemStack = useOnContext.getItemInHand();
                boolean bl = !player.getAbilities().instabuild && itemStack.getCount() == 1;
                if (bl) {
                    this.addLodestoneTags(level.dimension(), blockPos, itemStack.getOrCreateTag());
                } else {
                    ItemStack itemStack2 = new ItemStack(this, 1);
                    CompoundTag compoundTag = itemStack.hasTag() ? itemStack.getTag().copy() : new CompoundTag();
                    itemStack2.setTag(compoundTag);
                    if (!player.getAbilities().instabuild) {
                        itemStack.shrink(1);
                    }

                    this.addLodestoneTags(level.dimension(), blockPos, compoundTag);
                    if (!player.getInventory().add(itemStack2)) {
                        player.drop(itemStack2, false);
                    }
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        private void addLodestoneTags(ResourceKey<Level> resourceKey, BlockPos blockPos, CompoundTag compoundTag) {
            compoundTag.put("LodestonePos", NbtUtils.writeBlockPos(blockPos));
            DataResult<Tag> data = Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, resourceKey);
            data.resultOrPartial(LOGGER::error).ifPresent((tag) -> {
                compoundTag.put("LodestoneDimension", tag);
            });
            compoundTag.putBoolean("LodestoneTracked", true);
        }


        @Override
        public String getDescriptionId()
        {
            return Items.COMPASS.getDescriptionId();
        }
    }

    public interface Dyeable
    {
        String TAG_COLOR = "color";
        String TAG_DISPLAY = "display";
        int DEFAULT_COLOR = 0xFF0000;

        default boolean hasCustomColor(ItemStack itemStack)
        {
            CompoundTag tag = itemStack.getTagElement(TAG_DISPLAY);
            return tag != null && tag.contains(TAG_COLOR, 99);
        }

        default int getColor(ItemStack itemStack)
        {
            CompoundTag tag = itemStack.getTagElement(TAG_DISPLAY);
            return tag != null && tag.contains(TAG_COLOR, 99) ? tag.getInt(TAG_COLOR) : DEFAULT_COLOR;
        }

        default void clearColor(ItemStack itemStack)
        {
            CompoundTag tag = itemStack.getTagElement(TAG_DISPLAY);
            if (tag != null && tag.contains(TAG_COLOR)) {
                tag.remove(TAG_COLOR);
            }

        }

        default void setColor(ItemStack itemStack, int i)
        {
            itemStack.getOrCreateTagElement(TAG_DISPLAY).putInt(TAG_COLOR, i);
        }

        static ItemStack dye(ItemStack inputStack, List<DyeItem> dyes)
        {
            ItemStack outputStack = ItemStack.EMPTY;
            int[] colors = new int[3];
            int i = 0;
            int mixCount = 0;
            Dyeable dyeable = null;
            Item item = inputStack.getItem();

            if (item instanceof Dyeable) {
                dyeable = (Dyeable) item;
                outputStack = inputStack.copy();
                outputStack.setCount(1);
                if (dyeable.hasCustomColor(inputStack)) {
                    int currentColor = dyeable.getColor(outputStack);
                    float r = (float) (currentColor >> 16 & 255) / 255.0F;
                    float g = (float) (currentColor >> 8 & 255) / 255.0F;
                    float b = (float) (currentColor & 255) / 255.0F;
                    i += (int) (Math.max(r, Math.max(g, b)) * 255.0F);
                    colors[0] += (int) (r * 255.0F);
                    colors[1] += (int) (g * 255.0F);
                    colors[2] += (int) (b * 255.0F);
                    ++mixCount;
                }

                for (DyeItem dyeItem : dyes) {
                    float[] dyeColor = dyeItem.getDyeColor().getTextureDiffuseColors();
                    int r = (int) (dyeColor[0] * 255.0F);
                    int g = (int) (dyeColor[1] * 255.0F);
                    int b = (int) (dyeColor[2] * 255.0F);
                    i += Math.max(r, Math.max(g, b));
                    colors[0] += r;
                    colors[1] += g;
                    colors[2] += b;
                    mixCount++;
                }
            }

            if (dyeable == null)
            {
                return ItemStack.EMPTY;
            } else {
                int finalR = colors[0] / mixCount;
                int finalG = colors[1] / mixCount;
                int finalB = colors[2] / mixCount;
                float h = (float) i / (float) mixCount;
                float q = (float) Math.max(finalR, Math.max(finalG, finalB));
                finalR = (int) ((float) finalR * h / q);
                finalG = (int) ((float) finalG * h / q);
                finalB = (int) ((float) finalB * h / q);
                int finalColor = (finalR << 8) + finalG;
                finalColor = (finalColor << 8) + finalB;
                dyeable.setColor(outputStack, finalColor);
                return outputStack;
            }
        }
    }

    public static class DyeRecipe extends CustomRecipe
    {

        public DyeRecipe(ResourceLocation resourceLocation, CraftingBookCategory craftingBookCategory)
        {
            super(resourceLocation, craftingBookCategory);
        }

        public boolean matches(CraftingContainer arg, Level arg2) {
            ItemStack itemStack = ItemStack.EMPTY;
            List<ItemStack> list = Lists.newArrayList();

            for(int i = 0; i < arg.getContainerSize(); ++i) {
                ItemStack itemStack2 = arg.getItem(i);
                if (!itemStack2.isEmpty()) {
                    if (itemStack2.getItem() instanceof Dyeable || itemStack2.is(Items.COMPASS))
                    {
                        if (!itemStack.isEmpty()) {
                            return false;
                        }

                        itemStack = itemStack2;
                    } else {
                        if (!(itemStack2.getItem() instanceof DyeItem)) {
                            return false;
                        }

                        list.add(itemStack2);
                    }
                }
            }

            return !itemStack.isEmpty() && !list.isEmpty();
        }

        public ItemStack assemble(CraftingContainer arg) {
            List<DyeItem> list = Lists.newArrayList();
            ItemStack itemStack = ItemStack.EMPTY;

            for(int i = 0; i < arg.getContainerSize(); ++i) {
                ItemStack itemStack2 = arg.getItem(i);
                if (!itemStack2.isEmpty()) {
                    Item item = itemStack2.getItem();
                    if (item instanceof Dyeable) {
                        if (!itemStack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }

                        itemStack = itemStack2.copy();
                    }
                    else if(item == Items.COMPASS)
                    {
                        if (!itemStack.isEmpty()) {
                            return ItemStack.EMPTY;
                        }

                        itemStack = new ItemStack(DYED_COMPASS.get(), itemStack2.getCount());
                        if(itemStack2.hasTag())
                            itemStack.setTag(itemStack2.getTag().copy());
                    }
                    else {
                        if (!(item instanceof DyeItem)) {
                            return ItemStack.EMPTY;
                        }

                        list.add((DyeItem)item);
                    }
                }
            }

            if (!itemStack.isEmpty() && !list.isEmpty()) {
                return Dyeable.dye(itemStack, list);
            } else {
                return ItemStack.EMPTY;
            }
        }

        public boolean canCraftInDimensions(int i, int j) {
            return i * j >= 2;
        }

        public RecipeSerializer<?> getSerializer() {
            return DYE.get();
        }
    }

}
