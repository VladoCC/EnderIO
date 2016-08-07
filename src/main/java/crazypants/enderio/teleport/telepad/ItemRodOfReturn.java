package crazypants.enderio.teleport.telepad;

import java.util.List;

import com.enderio.core.api.client.gui.IResourceTooltipProvider;
import com.enderio.core.common.transform.EnderCoreMethods.IOverlayRenderAware;

import cofh.api.energy.ItemEnergyContainer;
import crazypants.enderio.EnderIO;
import crazypants.enderio.EnderIOTab;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.teleport.ITelePad;
import crazypants.enderio.api.teleport.TravelSource;
import crazypants.enderio.config.Config;
import crazypants.enderio.item.PowerBarOverlayRenderHelper;
import crazypants.enderio.machine.power.PowerDisplayUtil;
import crazypants.enderio.teleport.TeleportUtil;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemRodOfReturn extends ItemEnergyContainer implements IResourceTooltipProvider, IOverlayRenderAware {

  public static ItemRodOfReturn create() {
    ItemRodOfReturn result = new ItemRodOfReturn();
    result.init();
    return result;
  }

  private static final int TICKS_TO_ACTIVATE = 50;

  private static final int POWER_BUFFER = Config.darkSteelPowerStorageLevelThree;
  private static final int TICKS_TO_CHARGE = 100;
  private static final int RF_MAX_INPUT = (int) Math.ceil(POWER_BUFFER / (double) TICKS_TO_CHARGE);
  private static final int RF_PER_TICK = 19000;

  private static final String KEY_LAST_USED_TICK = "lastUsedAt";

  protected ItemRodOfReturn() {
    super(POWER_BUFFER, RF_MAX_INPUT, 0);
    setCreativeTab(EnderIOTab.tabEnderIO);
    setUnlocalizedName(ModObject.itemRodOfReturn.getUnlocalisedName());
    setRegistryName(ModObject.itemRodOfReturn.getUnlocalisedName());
    setMaxStackSize(1);
    setHasSubtypes(true);
  }

  protected void init() {
    GameRegistry.register(this);
  }

  @Override
  public EnumActionResult onItemUseFirst(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
      EnumHand hand) {

    if (world.isRemote) {
      // If we dont return pass on the client this wont get called on the server
      return EnumActionResult.PASS;
    }
    TileEntity te = world.getTileEntity(pos);
    if (te instanceof ITelePad && player.isSneaking()) {
      ITelePad tp = (ITelePad)te;
      pos = tp.getMaster().getLocation().getBlockPos();
      setTarget(stack, pos, world.provider.getDimension());
      player.addChatMessage(
          new TextComponentString(EnderIO.lang.localize("itemRodOfReturn.chat.sync") + " [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]"));
      player.stopActiveHand();
      return EnumActionResult.SUCCESS;
    }

    return EnumActionResult.PASS;
  }

  @Override
  public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand) {
    long lastUsed = getLastUsedTick(stack);
    if ( (lastUsed < 0 || (world.getTotalWorldTime() - lastUsed) > 20) && getEnergyStored(stack) > 0) {
      player.setActiveHand(hand);

    }
    return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
  }

  @Override
  public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
    int used = (60 - count) * 1000;
    int newVal = getEnergyStored(stack) - used;
    if (newVal < 0) { 
      if (player.worldObj.isRemote) {
        player.addChatMessage(new TextComponentString(EnderIO.lang.localize("itemRodOfReturn.chat.notEnoughPower", TextFormatting.RED.toString())));
      } 
      player.stopActiveHand();
    }
  }

  @Override
  public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase player, int timeLeft) {
    updateStackNBT(stack, world, timeLeft);      
  }

  @Override
  public ItemStack onItemUseFinish(ItemStack stack, World worldIn, EntityLivingBase entityLiving) {
    if (updateStackNBT(stack, worldIn, 0)) {
      System.out.println("ItemRodOfReturn.onItemUseFinish: Do teleport ");
      BlockPos pos = getTargetPos(stack);
      if (pos == null) {        
        if (worldIn.isRemote) {
          entityLiving.addChatMessage(new TextComponentString(EnderIO.lang.localize("itemRodOfReturn.chat.targetNotSet", TextFormatting.RED.toString())));
        }
        return stack;
      }
      int dim = getTargetDimension(stack);
      TeleportUtil.doTeleport(entityLiving, pos, dim, false, TravelSource.TELEPAD);
    } else if(worldIn.isRemote) {
      entityLiving.addChatMessage(new TextComponentString(EnderIO.lang.localize("itemRodOfReturn.chat.notEnoughPower", TextFormatting.RED.toString())));
    }    
    return stack;
  }

  @Override
  public boolean shouldCauseReequipAnimation(ItemStack oldS, ItemStack newS, boolean slotChanged) {
    return slotChanged || oldS == null || newS == null || oldS.getItem() != newS.getItem();
  }

  @Override
  public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
    return shouldCauseReequipAnimation(oldStack, newStack, false);
  }

  @Override
  public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
    return true;
  }

  @Override
  public void onCreated(ItemStack itemStack, World world, EntityPlayer entityPlayer) {
    setEnergy(itemStack, 0);
  }

  @Override
  public EnumAction getItemUseAction(ItemStack stack) {
    return EnumAction.BOW;
  }

  @Override
  public int getMaxItemUseDuration(ItemStack stack) {
    return TICKS_TO_ACTIVATE;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addInformation(ItemStack itemStack, EntityPlayer par2EntityPlayer, List<String> list, boolean par4) {
    super.addInformation(itemStack, par2EntityPlayer, list, par4);
    String str = PowerDisplayUtil.formatPower(getEnergyStored(itemStack)) + "/" + PowerDisplayUtil.formatPower(getMaxEnergyStored(itemStack)) + " "
        + PowerDisplayUtil.abrevation();
    list.add(str);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void getSubItems(Item item, CreativeTabs par2CreativeTabs, List<ItemStack> par3List) {
    ItemStack is = new ItemStack(this);
    par3List.add(is);

    is = new ItemStack(this);
    setFull(is);
    par3List.add(is);
  }

  @Override
  public String getUnlocalizedNameForTooltip(ItemStack stack) {
    return getUnlocalizedName();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public boolean isFull3D() {
    return true;
  }

  @Override
  public void renderItemOverlayIntoGUI(ItemStack stack, int xPosition, int yPosition) {
    PowerBarOverlayRenderHelper.instance.render(stack, xPosition, yPosition);
  }

  // --------------------- NBT Handling for stacks

  private boolean updateStackNBT(ItemStack stack, World world, int timeLeft) {
    setLastUsedTick(stack, world.getTotalWorldTime());
     // half a second before it costs you
    if (timeLeft > (TICKS_TO_ACTIVATE - 10)) {       
      return false;
    }
    return useEnergy(stack, timeLeft);
  }

  private long getLastUsedTick(ItemStack stack) {
    if (stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(KEY_LAST_USED_TICK)) {
      return -1;
    }
    return stack.getTagCompound().getLong(KEY_LAST_USED_TICK);
  }

  private void setLastUsedTick(ItemStack stack, long tick) {
    if (!stack.hasTagCompound()) {
      stack.setTagCompound(new NBTTagCompound());
    }
    stack.getTagCompound().setLong(KEY_LAST_USED_TICK, tick);
  }

  private boolean useEnergy(ItemStack stack, int timeLeft) {
    int used = (TICKS_TO_ACTIVATE - timeLeft) * RF_PER_TICK;
    int newVal = getEnergyStored(stack) - used;
    if (newVal < 0) {
      setEnergy(stack, 0);
      return false;
    }
    setEnergy(stack, newVal);
    return true;
  }

  private void setEnergy(ItemStack container, int energy) {
    if (container.getTagCompound() == null) {
      container.setTagCompound(new NBTTagCompound());
    }
    container.getTagCompound().setInteger("Energy", energy);
  }

  private void setFull(ItemStack container) {
    setEnergy(container, POWER_BUFFER);
  }

  private void setTarget(ItemStack container, BlockPos pos, int dimension) {
    if (container.getTagCompound() == null) {
      container.setTagCompound(new NBTTagCompound());
    }
    container.getTagCompound().setLong("targetPos", pos.toLong());
    container.getTagCompound().setInteger("targetDim", dimension);
  }

  private BlockPos getTargetPos(ItemStack stack) {
    if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("targetPos")) {
      return null;
    }
    return BlockPos.fromLong(stack.getTagCompound().getLong("targetPos"));
  }

  private int getTargetDimension(ItemStack stack) {
    if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("targetDim")) {
      return -1;
    }
    return stack.getTagCompound().getInteger("targetDim");
  }

}