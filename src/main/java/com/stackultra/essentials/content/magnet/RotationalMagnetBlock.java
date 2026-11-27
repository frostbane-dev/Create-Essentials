package com.stackultra.essentials.content.magnet;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import java.util.List;

public class RotationalMagnetBlock extends KineticBlock implements IBE<RotationalMagnetBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 5.5, 14.0, 12.0, 10.5),
            Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0));

    private final MagnetTier tier;

    public RotationalMagnetBlock(MagnetTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.DOWN));
    }

    public MagnetTier getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Always place with the charging/kinetic shaft input oriented upward (+Y)
        return defaultBlockState().setValue(FACING, Direction.DOWN);
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
        return SHAPE;
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
        if (!tier.hasFilterSlots())
            return InteractionResult.PASS;
        withBlockEntityDo(level, pos, be -> player.openMenu(be, be::sendToMenu));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (!tier.hasFilterSlots())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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

        ItemStack drop = new ItemStack(tier.hasFilterSlots()
                ? AllItems.PRECISION_MAGNET.get()
                : AllItems.ROTATIONAL_MAGNET.get());

        if (blockEntity instanceof RotationalMagnetBlockEntity be) {
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
        ItemStack drop = new ItemStack(tier.hasFilterSlots()
                ? AllItems.PRECISION_MAGNET.get()
                : AllItems.ROTATIONAL_MAGNET.get());

        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof RotationalMagnetBlockEntity be) {
            be.saveToStack(drop);
            int capacityLevel = be.getCapacityEnchantLevel();
            if (capacityLevel > 0)
                drop.enchant(params.getLevel().holderOrThrow(AllEnchantments.CAPACITY), capacityLevel);
        }

        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(tier.hasFilterSlots()
                ? AllItems.PRECISION_MAGNET.get()
                : AllItems.ROTATIONAL_MAGNET.get());

        getBlockEntityOptional(level, pos).ifPresent(be -> {
            be.saveToStack(stack);
            if (be.getCapacityEnchantLevel() > 0 && level instanceof Level actualLevel)
                stack.enchant(actualLevel.holderOrThrow(AllEnchantments.CAPACITY), be.getCapacityEnchantLevel());
        });

        return stack;
    }

    @Override
    public Class<RotationalMagnetBlockEntity> getBlockEntityClass() {
        return RotationalMagnetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RotationalMagnetBlockEntity> getBlockEntityType() {
        return AllBlockEntities.ROTATIONAL_MAGNET.get();
    }
}
