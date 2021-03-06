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

package techreborn.tiles.cable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import reborncore.api.IListInfoProvider;
import reborncore.api.IToolDrop;
import reborncore.common.RebornCoreConfig;
import reborncore.common.powerSystem.PowerSystem;
import reborncore.common.util.StringUtils;
import techreborn.blocks.cable.BlockCable;
import techreborn.blocks.cable.EnumCableType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by modmuss50 on 19/05/2017.
 */

public class TileCable extends TileEntity 
	implements ITickable, IEnergyStorage, IListInfoProvider, IToolDrop {
	
	public int power = 0;
	private int transferRate = 0;
	private EnumCableType cableType = null;
	private ArrayList<EnumFacing> sendingFace = new ArrayList<EnumFacing>();
	int ticksSinceLastChange = 0;
	
	//MC calls this during world load. Keep it, please.
	public TileCable() {
		super();
	}
	
	public TileCable(EnumCableType cableType) {
		this.cableType = cableType;
		this.transferRate = cableType.transferRate * RebornCoreConfig.euPerFU;
	}
	
	private void updateCableType() {
		if (cableType == null) {
			cableType = world.getBlockState(pos).getValue(BlockCable.TYPE);
			transferRate = cableType.transferRate * RebornCoreConfig.euPerFU;
		}
		return;
	}
	
	public boolean canReceiveFromFace(EnumFacing face) {
		if (sendingFace.contains(face)) {
			return false;
		}
		return canReceive();
	}
		
	// TileEntity
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityEnergy.ENERGY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}
	
	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityEnergy.ENERGY) {
			return CapabilityEnergy.ENERGY.cast(this);
		}
		return super.getCapability(capability, facing);
	}
	
	@Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound nbtTag = new NBTTagCompound();
        writeToNBT(nbtTag);
        return new SPacketUpdateTileEntity(getPos(), 1, nbtTag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("TileCable")) {
            power = compound.getCompoundTag("TileCable").getInteger("power");
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (power > 0) {
        	NBTTagCompound data = new NBTTagCompound();
    		data.setInteger("power", getEnergyStored());
    		compound.setTag("TileCable", data);
        }
        return compound;
    }
	
	// ITickable
	@Override
	public void update() {
		if (world.isRemote) {
			return;
		}
		
		if (cableType == null ){
			updateCableType(); 
		}
		
		ticksSinceLastChange++;
		if (ticksSinceLastChange >= 10) {
			sendingFace.clear();
			ticksSinceLastChange = 0;		
		}
		
		if (!canExtract()) {
			return;
		}

		ArrayList<IEnergyStorage> acceptors = new ArrayList<IEnergyStorage>();
		for (EnumFacing face : EnumFacing.VALUES) {
			TileEntity tile = world.getTileEntity(pos.offset(face));

			if (tile == null) {
				continue;
			} else if (tile instanceof TileCable) {
				TileCable cable = (TileCable) tile;
				if (power > cable.power && cable.canReceiveFromFace(face.getOpposite())) {
					acceptors.add((IEnergyStorage) tile);
					if (!sendingFace.contains(face)) {
						sendingFace.add(face);
					}					
				}
			} else if (tile.hasCapability(CapabilityEnergy.ENERGY, face.getOpposite())) {
				IEnergyStorage energyTile = tile.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
				if (energyTile != null && energyTile.canReceive()) {
					acceptors.add(energyTile);
				}
			}
		}
			
		if (acceptors.size() > 0 ) {
			for (IEnergyStorage tile : acceptors) {
				int drain = Math.min(power, transferRate);
				if (drain > 0 && tile.receiveEnergy(drain, true) > 0) {
					int move = tile.receiveEnergy(drain, false);
					extractEnergy(move, false);
				}
			}
		}
	}

	// IEnergyStorage
	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if (!canReceive()) {
			return 0;
		}

		int energyReceived = Math.min(getMaxEnergyStored() - getEnergyStored(), Math.min(transferRate, maxReceive));
		if (!simulate) {
			power += energyReceived;
		}
		return energyReceived;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		if (!canExtract()) {
			return 0;
		}

		int energyExtracted = Math.min(getEnergyStored(), Math.min(transferRate, maxExtract));
		if (!simulate) {
			power -= energyExtracted;
		}
		return energyExtracted;
	}

	@Override
	public int getEnergyStored() {
		return power;
	}

	@Override
	public int getMaxEnergyStored() {
		return transferRate * 5;
	}

	@Override
	public boolean canExtract() {
		if (getEnergyStored() == 0 ) {
			return false;
		}
		return true;
	}

	@Override
	public boolean canReceive() {
		if (getMaxEnergyStored() == getEnergyStored()) {
			return false;
		}
		return true;
	}

    // IListInfoProvider
	@Override
	public void addInfo(List<String> info, boolean isRealTile) {
			info.add(TextFormatting.GRAY + StringUtils.t("techreborn.tooltip.transferRate") + ": "
					+ TextFormatting.GOLD
					+ PowerSystem.getLocaliszedPowerFormatted(transferRate / RebornCoreConfig.euPerFU) + "/t");
			info.add(TextFormatting.GRAY + StringUtils.t("techreborn.tooltip.tier") + ": "
					+ TextFormatting.GOLD + StringUtils.toFirstCapitalAllLowercase(cableType.tier.toString()));
	}

	// IToolDrop
	@Override
	public ItemStack getToolDrop(EntityPlayer playerIn) {
		return cableType.getStack();
	}
}
