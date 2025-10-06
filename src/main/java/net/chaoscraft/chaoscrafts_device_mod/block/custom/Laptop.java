package net.chaoscraft.chaoscrafts_device_mod.block.custom;

import net.chaoscraft.chaoscrafts_device_mod.block.ModBlocks;
import net.chaoscraft.chaoscrafts_device_mod.block.entity.LaptopEntity;
import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.chaoscraft.chaoscrafts_device_mod.util.LaptopHitboxHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;

import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

public class Laptop extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public Laptop(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, Boolean.valueOf(false)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new LaptopEntity(blockPos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof LaptopEntity laptop) {
                laptop.setWhiteVariant(pState.getBlock() == ModBlocks.LAPTOP_WHITE.get());
                laptop.setOpen(false, false);
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        if (!state.is(this)) {
            return LaptopHitboxHelper.getShapeForState(false, Direction.NORTH);
        }

        Direction facing = state.getValue(FACING);
        boolean isOpen = state.getValue(OPEN);

        return LaptopHitboxHelper.getShapeForState(isOpen, facing);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return getShape(state, world, pos, ctx);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.is(this)) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof LaptopEntity laptop)) return InteractionResult.PASS;

        Vec3 hitVec = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction facing = state.getValue(FACING);

        boolean onScreen = LaptopHitboxHelper.isPointOnScreen(hitVec, laptop.isOpen(), facing);

        if (!level.isClientSide) {
            if (!player.isShiftKeyDown() || onScreen) {
                if (onScreen && laptop.isOpen()) {
                    return InteractionResult.PASS;
                } else {
                    laptop.toggleOpen();
                    BlockState newState = state.setValue(OPEN, laptop.isOpen());
                    level.setBlock(pos, newState, 3);
                    level.playSound(null, pos, laptop.isOpen() ? ModSounds.LAPTOP_OPEN.get() : ModSounds.LAPTOP_CLOSE.get(), SoundSource.BLOCKS, 0.6f, 1.0f + (level.random.nextFloat()-0.5f)*0.1f);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        if (laptop.isOpen() && onScreen) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> (Runnable) () -> {
                try {
                    Class<?> clientClass = Class.forName("net.chaoscraft.chaoscrafts_device_mod.client.LaptopClient");
                    clientClass.getMethod("openDesktopScreen").invoke(null);
                } catch (ClassNotFoundException cnfe) {
                } catch (Exception e) {
                }
            });
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LaptopEntity) {
                level.removeBlockEntity(pos);
            }
        }
    }
}