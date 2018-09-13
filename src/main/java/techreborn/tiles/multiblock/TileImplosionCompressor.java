/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018 TechReborn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package techreborn.tiles.multiblock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.registration.RebornRegistry;
import reborncore.common.registration.impl.ConfigRegistry;
import reborncore.common.util.Inventory;
import techreborn.api.Reference;
import techreborn.client.container.IContainerProvider;
import techreborn.client.container.builder.BuiltContainer;
import techreborn.client.container.builder.ContainerBuilder;
import techreborn.init.ModBlocks;
import techreborn.lib.ModInfo;
import techreborn.tiles.TileGenericMachine;

@RebornRegistry(modID = ModInfo.MOD_ID)
public class TileImplosionCompressor extends TileGenericMachine	implements IContainerProvider {
	
	@ConfigRegistry(config = "machines", category = "implosion_compressor", key = "ImplosionCompressorMaxInput", comment = "Implosion Compressor Max Input (Value in EU)")
	public static int maxInput = 64;
	@ConfigRegistry(config = "machines", category = "implosion_compressor", key = "ImplosionCompressorMaxEnergy", comment = "Implosion Compressor Max Energy (Value in EU)")
	public static int maxEnergy = 64_000;

	public MultiblockChecker multiblockChecker;

	public TileImplosionCompressor() {
		super("ImplosionCompressor", maxInput, maxEnergy, ModBlocks.IMPLOSION_COMPRESSOR, 4);
		final int[] inputs = new int[] { 0, 1 };
		final int[] outputs = new int[] { 2, 3 };
		this.inventory = new Inventory<>(5, "TileImplosionCompressor", 64, this);
		this.crafter = new RecipeCrafter(Reference.IMPLOSION_COMPRESSOR_RECIPE, this, 2, 2, this.inventory, inputs, outputs);
	}
	
	public boolean getMutliBlock() {
		final boolean down = multiblockChecker.checkRectY(1, 1, MultiblockChecker.REINFORCED_CASING, MultiblockChecker.ZERO_OFFSET);
		final boolean up = multiblockChecker.checkRectY(1, 1, MultiblockChecker.REINFORCED_CASING, new BlockPos(0, 2, 0));
		final boolean chamber = multiblockChecker.checkRingYHollow(1, 1, MultiblockChecker.REINFORCED_CASING, new BlockPos(0, 1, 0));
		return down && chamber && up;
	}

	// TileGenericMachine
	@Override
	public void update() {
		if (multiblockChecker == null) {
			final BlockPos downCenter = pos.offset(getFacing().getOpposite(), 2);
			multiblockChecker = new MultiblockChecker(world, downCenter);
		}
		
		if (!world.isRemote && getMutliBlock()){ 
			super.update();
		}	
	}
	
	// TileEntity
	@Override
	public void validate() {
		super.validate();
		multiblockChecker = new MultiblockChecker(world, pos.down(3));
	}

	// IContainerProvider
	@Override
	public BuiltContainer createContainer(final EntityPlayer player) {
		return new ContainerBuilder("implosioncompressor").player(player.inventory).inventory().hotbar().addInventory()
				.tile(this).slot(0, 50, 27).slot(1, 50, 47).outputSlot(2, 92, 36).outputSlot(3, 110, 36)
				.energySlot(4, 8, 72).syncEnergyValue().syncCrafterValue().addInventory().create(this);
	}
}
