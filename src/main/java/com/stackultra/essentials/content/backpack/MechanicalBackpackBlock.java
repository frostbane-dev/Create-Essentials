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

    private static final VoxelShape SHAPE_NORTH = Block.box(1.0, 0.0, 1.0, 15.0, 15.5, 13.5);
    private static final VoxelShape SHAPE_SOUTH = Block.box(1.0, 0.0, 2.5, 15.0, 15.5, 15.0);
    private static final VoxelShape SHAPE_EAST = Block.box(2.5, 0.0, 1.0, 15.0, 15.5, 15.0);
    private static final VoxelShape SHAPE_WEST = Block.box(1.0, 0.0, 1.0, 13.5, 15.5, 15.0);

    public MechanicalBackpackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(NETHERITE, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NETHERITE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(NETHERITE, context.getItemInHand().getItem() instanceof NetheriteBackpackItem)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be) {
            if (player.openMenu(be, be::sendToMenu).isPresent())
                BackpackSounds.playOpenSound(level, pos);
        }
        return ItemInteractionResult.SUCCESS;
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

        if (!player.getInventory().add(drop))
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
