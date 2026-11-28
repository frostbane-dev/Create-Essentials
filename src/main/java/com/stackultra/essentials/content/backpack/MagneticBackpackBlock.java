package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllEnchantments;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

import java.util.List;

public class MagneticBackpackBlock extends KineticBlock implements IBE<MagneticBackpackBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 0.0, 3.0, 14.0, 16.0, 12.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 0.0, 4.0, 14.0, 16.0, 13.0);
    private static final VoxelShape SHAPE_EAST = Block.box(4.0, 0.0, 2.0, 13.0, 16.0, 14.0);
    private static final VoxelShape SHAPE_WEST = Block.box(3.0, 0.0, 2.0, 12.0, 16.0, 14.0);

    public MagneticBackpackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private static Direction horizontalFacing(BlockState state) {
        Direction dir = state.getValue(FACING);
        return dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(horizontalFacing(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction dir = horizontalFacing(state);
        return state.setValue(FACING, mirror.getRotation(dir).rotate(dir));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction current = horizontalFacing(originalState);
        Direction next = targetedFace == Direction.DOWN ? current.getCounterClockWise() : current.getClockWise();
        return originalState.setValue(FACING, next);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.UP;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
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
        if (level.getBlockEntity(pos) instanceof MagneticBackpackBlockEntity be)
            return ItemHandlerHelper.calcRedstoneFromInventory(be.getItemHandler());
        return 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return pickupPlacedBackpack(level, pos, player);
        }
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, be -> {
            if (player.openMenu(be, be::sendToMenu).isPresent())
                BackpackSounds.playOpenSound(level, pos);
        });
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (player.isShiftKeyDown()) {
            return pickupPlacedBackpack(level, pos, player).consumesAction()
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, be -> {
            if (player.openMenu(be, be::sendToMenu).isPresent())
                BackpackSounds.playOpenSound(level, pos);
        });
        return ItemInteractionResult.SUCCESS;
    }

    private InteractionResult pickupPlacedBackpack(Level level, BlockPos pos, Player player) {
        if (!player.mayBuild())
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack drop = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        if (blockEntity instanceof MagneticBackpackBlockEntity be) {
            be.saveToStack(drop);
            applyLegacyCapacityIfMissing(drop, be, level);
        }

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
        withBlockEntityDo(level, pos, be -> be.loadFromStack(stack));
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (level.isClientSide())
            return;

        ItemStack drop = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        if (blockEntity instanceof MagneticBackpackBlockEntity be) {
            be.saveToStack(drop);
            applyLegacyCapacityIfMissing(drop, be, level);
        }

        popResource(level, pos, drop);

        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack drop = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());

        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof MagneticBackpackBlockEntity be) {
            be.saveToStack(drop);
            applyLegacyCapacityIfMissing(drop, be, params.getLevel());
        }

        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());

        getBlockEntityOptional(level, pos).ifPresent(be -> {
            be.saveToStack(stack);
            if (level instanceof Level actualLevel)
                applyLegacyCapacityIfMissing(stack, be, actualLevel);
        });

        return stack;
    }

    private static void applyLegacyCapacityIfMissing(ItemStack stack, MagneticBackpackBlockEntity be, Level level) {
        int capacityLevel = be.getCapacityEnchantLevel();
        if (capacityLevel > 0 && MagneticBackpackItem.getCapacityLevel(stack) == 0) {
            stack.enchant(level.holderOrThrow(AllEnchantments.CAPACITY), capacityLevel);
        }
    }

    @Override
    public Class<MagneticBackpackBlockEntity> getBlockEntityClass() {
        return MagneticBackpackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MagneticBackpackBlockEntity> getBlockEntityType() {
        return AllBlockEntities.MAGNETIC_BACKPACK.get();
    }
}
