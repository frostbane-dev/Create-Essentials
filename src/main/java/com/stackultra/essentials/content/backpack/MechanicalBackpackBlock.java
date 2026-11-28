package com.stackultra.essentials.content.backpack;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stackultra.essentials.registry.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

import java.util.List;

public class MechanicalBackpackBlock extends Block implements EntityBlock, IWrenchable {

    public static final BooleanProperty NETHERITE = BooleanProperty.create("netherite");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public MechanicalBackpackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NETHERITE, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(COLOR, DyeColor.BROWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NETHERITE, FACING, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        DyeColor color = BackpackColorHelper.resolveDyeColor(stack);
        return defaultBlockState()
                .setValue(NETHERITE, stack.getItem() instanceof NetheriteBackpackItem)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(COLOR, color != null ? color : DyeColor.BROWN);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction current = originalState.getValue(FACING);
        Direction next = targetedFace == Direction.DOWN ? current.getCounterClockWise() : current.getClockWise();
        return originalState.setValue(FACING, next);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BackpackShapes.mechanical(state.getValue(FACING));
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be)
            return ItemHandlerHelper.calcRedstoneFromInventory(be.getItemHandler());
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalBackpackBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return pickupPlacedBackpack(state, level, pos, player);
        }
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be) {
            if (player.openMenu(be, be::sendToMenu).isPresent())
                BackpackSounds.playOpenSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (player.isShiftKeyDown()) {
            return pickupPlacedBackpack(state, level, pos, player).consumesAction()
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be) {
            if (player.openMenu(be, be::sendToMenu).isPresent())
                BackpackSounds.playOpenSound(level, pos);
        }
        return ItemInteractionResult.SUCCESS;
    }

    private InteractionResult pickupPlacedBackpack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.mayBuild())
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack drop = new ItemStack(dropItem(state, blockEntity));
        if (blockEntity instanceof MechanicalBackpackBlockEntity be)
            be.saveToStack(drop);

        if (!level.removeBlock(pos, false))
            return InteractionResult.PASS;

        int selected = player.getInventory().selected;
        if (selected >= 0 && selected < 9 && player.getInventory().getItem(selected).isEmpty()) {
            player.getInventory().setItem(selected, drop);
        } else if (!player.getInventory().add(drop)) {
            popResource(level, pos, drop);
        }
        player.inventoryMenu.broadcastChanges();

        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOL_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || stack.isEmpty())
            return;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be)
            be.loadFromStack(stack);
    }

    private static Item dropItem(BlockState state, @Nullable BlockEntity blockEntity) {
        boolean isNetherite = (blockEntity instanceof MechanicalBackpackBlockEntity be && be.isNetherite())
                || (state.hasProperty(NETHERITE) && state.getValue(NETHERITE));
        return isNetherite
                ? AllItems.NETHERITE_BACKPACK.get()
                : AllItems.MECHANICAL_BACKPACK.get();
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (level.isClientSide())
            return;

        ItemStack drop = new ItemStack(dropItem(state, blockEntity));
        if (blockEntity instanceof MechanicalBackpackBlockEntity be)
            be.saveToStack(drop);

        popResource(level, pos, drop);

        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack drop = new ItemStack(dropItem(state, blockEntity));
        if (blockEntity instanceof MechanicalBackpackBlockEntity be)
            be.saveToStack(drop);
        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(dropItem(state, level.getBlockEntity(pos)));
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be)
            be.saveToStack(stack);
        return stack;
    }
}
