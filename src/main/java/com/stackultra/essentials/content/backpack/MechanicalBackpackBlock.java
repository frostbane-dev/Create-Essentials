package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllItems;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

import java.util.List;

public class MechanicalBackpackBlock extends Block implements EntityBlock {

    public static final BooleanProperty NETHERITE = BooleanProperty.create("netherite");

    private static final VoxelShape SHAPE = Block.box(1.5, 0.0, 1.5, 14.5, 15.5, 13.0);

    public MechanicalBackpackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NETHERITE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NETHERITE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(NETHERITE,
                context.getItemInHand().getItem() instanceof NetheriteBackpackItem);
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalBackpackBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be)
            player.openMenu(be, be::sendToMenu);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MechanicalBackpackBlockEntity be)
            player.openMenu(be, be::sendToMenu);
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
