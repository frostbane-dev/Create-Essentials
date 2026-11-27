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

import javax.annotation.Nullable;

import java.util.List;

public class MagneticBackpackBlock extends KineticBlock implements IBE<MagneticBackpackBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE_NORTH = Block.box(1.5, 0.0, 1.5, 14.5, 16.0, 13.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(1.5, 0.0, 3.0, 14.5, 16.0, 14.5);
    private static final VoxelShape SHAPE_EAST = Block.box(3.0, 0.0, 1.5, 14.5, 16.0, 14.5);
    private static final VoxelShape SHAPE_WEST = Block.box(1.5, 0.0, 1.5, 13.0, 16.0, 14.5);

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
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return true;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, be -> player.openMenu(be, be::sendToMenu));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        withBlockEntityDo(level, pos, be -> player.openMenu(be, be::sendToMenu));
        return ItemInteractionResult.SUCCESS;
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
            int capacityLevel = be.getCapacityEnchantLevel();
            if (capacityLevel > 0)
                drop.enchant(level.holderOrThrow(AllEnchantments.CAPACITY), capacityLevel);
        }

        if (!player.getInventory().add(drop))
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
            int capacityLevel = be.getCapacityEnchantLevel();
            if (capacityLevel > 0)
                drop.enchant(params.getLevel().holderOrThrow(AllEnchantments.CAPACITY), capacityLevel);
        }

        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());

        getBlockEntityOptional(level, pos).ifPresent(be -> {
            be.saveToStack(stack);
            if (be.getCapacityEnchantLevel() > 0 && level instanceof Level actualLevel)
                stack.enchant(actualLevel.holderOrThrow(AllEnchantments.CAPACITY), be.getCapacityEnchantLevel());
        });

        return stack;
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
