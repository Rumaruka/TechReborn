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

package techreborn.items.tools;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import reborncore.common.powerSystem.ExternalPowerSystems;
import reborncore.common.powerSystem.forge.ForgePowerItemManager;
import reborncore.common.util.ChatUtils;
import reborncore.common.util.ItemUtils;
import techreborn.config.ConfigTechReborn;
import techreborn.init.ModItems;
import techreborn.lib.MessageIDs;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemAdvancedDrill extends ItemDrill {

	public ItemAdvancedDrill() {
		super(ToolMaterial.DIAMOND, "techreborn.advancedDrill", ConfigTechReborn.AdvancedDrillCharge, 2.0F, 10F);
		this.cost = 250;
		this.transferLimit = 1000;
	}

	public Set<BlockPos> getTargetBlocks(World worldIn, BlockPos pos, @Nullable EntityPlayer playerIn) {
		Set<BlockPos> targetBlocks = new HashSet<BlockPos>();
		if (!(playerIn instanceof EntityPlayer)) {
			return new HashSet<BlockPos>();
		}
		RayTraceResult raytrace = rayTrace(worldIn, playerIn, false);
		if(raytrace == null || raytrace.sideHit == null){
			return Collections.emptySet();
		}
		EnumFacing enumfacing = raytrace.sideHit;
		if (enumfacing == EnumFacing.SOUTH || enumfacing == EnumFacing.NORTH) {
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					BlockPos newPos = pos.add(i, j, 0);
					if (shouldBreak(playerIn, worldIn, pos, newPos)) {
						targetBlocks.add(newPos);
					}
				}
			}
		} else if (enumfacing == EnumFacing.EAST || enumfacing == EnumFacing.WEST) {
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					BlockPos newPos = pos.add(0, j, i);
					if (shouldBreak(playerIn, worldIn, pos, newPos)) {
						targetBlocks.add(newPos);
					}
				}
			}
		} else if (enumfacing == EnumFacing.DOWN || enumfacing == EnumFacing.UP) {
			for (int i = -1; i < 2; i++) {
				for (int j = -1; j < 2; j++) {
					BlockPos newPos = pos.add(j, 0, i);
					if (shouldBreak(playerIn, worldIn, pos, newPos)) {
						targetBlocks.add(newPos);
					}
				}
			}
		}
		return targetBlocks;
	}

	public void breakBlock(BlockPos pos, World world, EntityPlayer playerIn, ItemStack drill) {
		IBlockState blockState = world.getBlockState(pos);

		ForgePowerItemManager capEnergy = new ForgePowerItemManager(drill);
		if(capEnergy.getEnergyStored() > cost){
			capEnergy.extractEnergy(cost, false);
			ExternalPowerSystems.requestEnergyFromArmor(capEnergy, playerIn);

			blockState.getBlock().removedByPlayer(blockState, world, pos, playerIn, true);
			blockState.getBlock().harvestBlock(world, playerIn, pos, blockState, world.getTileEntity(pos), drill);
			world.setBlockToAir(pos);
			world.removeTileEntity(pos);
		}
	}
	
	private boolean shouldBreak(EntityPlayer playerIn, World worldIn, BlockPos originalPos, BlockPos pos) {
		if (originalPos.equals(pos)) {
			return false;
		}
		IBlockState blockState = worldIn.getBlockState(pos);
		if (blockState.getMaterial() == Material.AIR) {
			return false;
		}
		if (blockState.getMaterial().isLiquid()) {
			return false;
		}
		float blockHardness = blockState.getPlayerRelativeBlockHardness(playerIn, worldIn, pos);
		if (blockHardness == -1.0F) {
			return false;
		}
		float originalHardness = worldIn.getBlockState(originalPos).getPlayerRelativeBlockHardness(playerIn, worldIn, originalPos);
		if ((originalHardness / blockHardness) > 10.0F) {
			return false;
		}
		
		return true;	
	}

	// ItemDrill
	@Override
	public boolean onBlockDestroyed(ItemStack stack, World worldIn, IBlockState blockIn, BlockPos pos, EntityLivingBase entityLiving) {
		EntityPlayer playerIn = null;
		if ((entityLiving instanceof EntityPlayer)) {
			playerIn = (EntityPlayer) entityLiving;
		}
		if(ItemUtils.isActive(stack)){
			for (BlockPos additionalPos : getTargetBlocks(worldIn, pos, playerIn)) {
				breakBlock(additionalPos, worldIn, playerIn, stack);
			}
		}
		return super.onBlockDestroyed(stack, worldIn, blockIn, pos, entityLiving);
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
		final ItemStack stack = player.getHeldItem(hand);
		if (player.isSneaking()) {
			if (new ForgePowerItemManager(stack).getEnergyStored() < cost) {
				ChatUtils.sendNoSpamMessages(MessageIDs.nanosaberID, new TextComponentString(
					TextFormatting.GRAY + I18n.format("techreborn.message.nanosaberEnergyErrorTo") + " "
						+ TextFormatting.GOLD + I18n
						.format("techreborn.message.nanosaberActivate")));
			} else {
				if (!ItemUtils.isActive(stack)) {
					if (stack.getTagCompound() == null) {
						stack.setTagCompound(new NBTTagCompound());
					}
					stack.getTagCompound().setBoolean("isActive", true);
					if (world.isRemote) {
						ChatUtils.sendNoSpamMessages(MessageIDs.nanosaberID, new TextComponentString(
							TextFormatting.GRAY + I18n.format("techreborn.message.setTo") + " "
								+ TextFormatting.GOLD + I18n
								.format("techreborn.message.nanosaberActive")));
					}
				} else {
					stack.getTagCompound().setBoolean("isActive", false);
					if (world.isRemote) {
						ChatUtils.sendNoSpamMessages(MessageIDs.nanosaberID, new TextComponentString(
							TextFormatting.GRAY + I18n.format("techreborn.message.setTo") + " "
								+ TextFormatting.GOLD + I18n
								.format("techreborn.message.nanosaberInactive")));
					}
				}
			}
			return new ActionResult<>(EnumActionResult.SUCCESS, stack);
		}
		return new ActionResult<>(EnumActionResult.PASS, stack);
	}

	@Override
	public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
		if (ItemUtils.isActive(stack) && new ForgePowerItemManager(stack).getEnergyStored() < cost) {
			if(worldIn.isRemote){
				ChatUtils.sendNoSpamMessages(MessageIDs.nanosaberID, new TextComponentString(
					TextFormatting.GRAY + I18n.format("techreborn.message.nanosaberEnergyError") + " "
						+ TextFormatting.GOLD + I18n
						.format("techreborn.message.nanosaberDeactivating")));
			}
			stack.getTagCompound().setBoolean("isActive", false);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		if (!ItemUtils.isActive(stack)) {
			tooltip.add(TextFormatting.RED + I18n.format("techreborn.message.nanosaberInactive"));
		} else {
			tooltip.add(TextFormatting.GREEN + I18n.format("techreborn.message.nanosaberActive"));
		}
	}

	// ItemPickaxe
	@Override
	public boolean canHarvestBlock(IBlockState blockIn) {
		return (Items.DIAMOND_PICKAXE.canHarvestBlock(blockIn) || Items.DIAMOND_SHOVEL.canHarvestBlock(blockIn)) && !Items.DIAMOND_AXE.canHarvestBlock(blockIn);
	}

	// Item
	@SideOnly(Side.CLIENT)
	@Override
	public void getSubItems(CreativeTabs par2CreativeTabs, NonNullList<ItemStack> itemList) {
		if (!isInCreativeTab(par2CreativeTabs)) {
			return;
		}
		ItemStack stack = new ItemStack(ModItems.ADVANCED_DRILL);
		ItemStack charged = stack.copy();
		ForgePowerItemManager capEnergy = new ForgePowerItemManager(charged);
		capEnergy.setEnergyStored(capEnergy.getMaxEnergyStored());

		itemList.add(stack);
		itemList.add(charged);
	}
}
