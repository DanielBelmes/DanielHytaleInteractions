package daniel.belmes.HytaleInteractions.interactions;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.consumer.TriIntConsumer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.codec.Vector3iArrayCodec;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.meta.DynamicMetaStore;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public class BlockRadiusInteraction extends SimpleBlockInteraction {
   public static final MetaKey<Set<BlockPosition>> HIT_BLOCKS = META_REGISTRY.registerMetaObject(i -> new HashSet<>());
   public static final MetaKey<DynamicMetaStore<Interaction>> SELECT_META_STORE = CONTEXT_META_REGISTRY
         .registerMetaObject(data -> null);
   protected Vector3i radius;
   protected Vector3i offset;
   protected String forBlock;

   BlockRadiusInteraction() {
      this.radius = new Vector3i();
      this.offset = new Vector3i();
   }

    protected enum MiningPlane {
      XZ,
      XY,
      ZY
   }

   protected void tick0(boolean firstRun, float time, InteractionType type,
         InteractionContext context, CooldownHandler cooldownHandler) {
      super.tick0(firstRun, time, type, context, cooldownHandler);
      this.computeCurrentBlockSyncData(context);
   }

   protected void interactWithBlock(
         World world,
         CommandBuffer<EntityStore> commandBuffer,
         InteractionType type,
         InteractionContext context,
         ItemStack heldItemStack,
         Vector3i targetBlock,
         CooldownHandler cooldownHandler) {
      Ref<EntityStore> ref = context.getEntity();
      Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
      if (playerComponent == null) {
         HytaleLogger.getLogger().at(Level.INFO).atMostEvery(5, TimeUnit.MINUTES)
               .log("BlockRadiusInteraction requires a Player but was used for: %s", ref);
      } else {
         if (forBlock != null) {
            DynamicMetaStore<Interaction> instanceStore = context.getInstanceStore();

            MiningPlane plane = getMiningPlaneFromPlayer(ref, playerComponent, targetBlock);

            Set<BlockPosition> forBlocks = (Set) instanceStore.getMetaObject(HIT_BLOCKS);
            RootInteraction rootForBlock = RootInteraction.getRootInteractionOrUnknown(this.forBlock);

            blocksFromRadius(targetBlock, offset, radius, plane, (x, y, z) -> {
               BlockPosition rawBlock = new BlockPosition(x, y, z);
               BlockPosition multiTargetBlock = world.getBaseBlock(rawBlock);
               if (forBlocks.add(multiTargetBlock)) {
                  InteractionContext subCtx = context.duplicate();
                  DynamicMetaStore<InteractionContext> metaStore = subCtx.getMetaStore();
                  metaStore.putMetaObject(TARGET_BLOCK, multiTargetBlock);
                  metaStore.putMetaObject(TARGET_BLOCK_RAW, rawBlock);
                  metaStore.putMetaObject(SELECT_META_STORE, instanceStore);
                  context.fork(subCtx, rootForBlock, false);
               }
            });

            if (forBlocks.isEmpty()) {
               context.getState().state = InteractionState.Failed;
            }
         }
      }
   }

   protected void simulateInteractWithBlock(
         InteractionType type, InteractionContext context, ItemStack itemInHand,
         World world, Vector3i targetBlock) {
   }

   private static void blocksFromRadius(Vector3i targetBlock, Vector3i offset, Vector3i radius,
         MiningPlane plane,
         TriIntConsumer consumer) {
      Vector3i offsetBlock = new Vector3i().add(targetBlock, offset);
      for (int x = -radius.x; x <= radius.x; x++) {
         for (int y = -radius.y; y <= radius.y; y++) {
            for (int z = -radius.z; z <= radius.z; z++) {
               switch (plane) {
                  case XZ:
                     consumer.accept(x + offsetBlock.x, offsetBlock.y, z + offsetBlock.z);
                     break;
                  case XY:
                     consumer.accept(x + offsetBlock.x, y + offsetBlock.y, offsetBlock.z);
                     break;
                  case ZY:
                     consumer.accept(offsetBlock.x, y + offsetBlock.y, z + offsetBlock.z);
                     break;
                  default:
                     continue;
               }
            }
         }
      }
   }

   // Adopted from
   // https://stackoverflow.com/questions/31640145/get-the-side-a-player-is-looking-on-the-block-bukkit
   // user andrewgazelka
   private static BlockFace blockFaceCollide(Vector3d startLocation, Vector3d direction, Box objectBoundry) {
      double directionX = direction.getX();
      double directionY = direction.getY();
      double directionZ = direction.getZ();
      Vector3d min = objectBoundry.min;
      Vector3d max = objectBoundry.max;

      if (directionY > 0) { // Looking +Y
         double b = min.y - startLocation.getY(); // Bottom of voxel Y - player position
         double tempConstant = b / directionY; // b / directionY
         if (tempConstant >= 0) {
            double xAtCollide = tempConstant * directionX + startLocation.getX();
            double zAtCollide = tempConstant * directionZ + startLocation.getZ();
            if (between(xAtCollide, min.x, max.x, 0)
                  && between(zAtCollide, min.z, max.z, 0)) {
               return BlockFace.DOWN;
            }
         }
      } else { // Looking -Y
         double e = max.y - startLocation.getY();
         double tempConstant = e / directionY;
         if (tempConstant >= 0) {
            double xAtCollide = tempConstant * directionX + startLocation.getX();
            double zAtCollide = tempConstant * directionZ + startLocation.getZ();
            if (between(xAtCollide, min.x, max.x, 0)
                  && between(zAtCollide, min.z, max.z, 0)) {
               return BlockFace.UP;
            }
         }
      }

      if (directionX > 0) {
         double d = min.x - startLocation.getX();
         double tempConstant = d / directionX;
         if (tempConstant >= 0) {
            double yAtCollide = tempConstant * directionY + startLocation.getY();
            double zAtCollide = tempConstant * directionZ + startLocation.getZ();
            if (between(yAtCollide, min.y, max.y, 0)
                  && between(zAtCollide, min.z, max.z, 0)) {
               return BlockFace.EAST;
            }
         }
      } else {
         double a = max.x - startLocation.getX();
         double tempConstant = a / directionX;
         if (tempConstant >= 0) {
            double yAtCollide = tempConstant * directionY + startLocation.getY();
            double zAtCollide = tempConstant * directionZ + startLocation.getZ();
            if (between(yAtCollide, min.y, max.y, 0)
                  && between(zAtCollide, min.z, max.z, 0)) {
               return BlockFace.WEST;
            }
         }
      }

      if (directionZ > 0) {
         double c = min.z - startLocation.getZ();
         double tempConstant = c / directionZ;
         if (tempConstant >= 0) {
            double yAtCollide = tempConstant * directionY + startLocation.getY();
            double xAtCollide = tempConstant * directionX + startLocation.getX();
            if (between(yAtCollide, min.y, max.y, 0)
                  && between(xAtCollide, min.x, max.x, 0)) {
               return BlockFace.NORTH;
            }
         }
      } else {
         double f = max.z - startLocation.getZ();
         double tempConstant = f / directionZ;
         if (tempConstant >= 0) {
            double yAtCollide = tempConstant * directionY + startLocation.getY();
            double xAtCollide = tempConstant * directionX + startLocation.getX();
            if (between(yAtCollide, min.y, max.y, 0)
                  && between(xAtCollide, min.x, max.x, 0)) {
               return BlockFace.SOUTH;
            }
         }
      }
      return null;
   }

   private static boolean between(double num, double a, double b, double EOF) {
      if (a <= b)
         return num + EOF >= a && num - EOF <= b;
      return num + EOF >= b && num - EOF <= a;
   }

   private static Box getAxisAllignedBoundBox(World world, Vector3i targetBlock) {
      BlockType blockType = world.getBlockType(targetBlock);
      if(blockType != null) {
         BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
         if(hitbox != null) {
            Box boundingBox = hitbox.get(0).getBoundingBox(); // Gotta deal with rotation if someone wants to use this for that
            return boundingBox.getBox(targetBlock.toVector3d());
         }
      }
      return null;
   }

   private static MiningPlane getMiningPlaneFromPlayer(Ref<EntityStore> ref,
         Player player, Vector3i targetBlock) {
      try {
         TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
         if (transform == null) {
            return MiningPlane.XY;
         }

         Vector3d playerPos = transform.getPosition();
         if (playerPos == null) {
            return MiningPlane.XY;
         }

         World world = player.getWorld();
         if (world == null) {
            return MiningPlane.XY;
         }

         HeadRotation headRotationComponent = ref.getStore().getComponent(ref, HeadRotation.getComponentType());
         assert headRotationComponent != null;
         Vector3d direction = headRotationComponent.getDirection();

         Transform look = TargetUtil.getLook(ref, ref.getStore());

         Box objectBoundry = getAxisAllignedBoundBox(world, targetBlock);
         if(objectBoundry == null) {
            return MiningPlane.XY;
         }

         BlockFace face = blockFaceCollide(look.getPosition(), direction, objectBoundry);
         if (face == null) {
            return MiningPlane.XY;
         }

         switch (face) {
            case BlockFace.NORTH:
            case BlockFace.SOUTH:
               return MiningPlane.XY;
            case BlockFace.EAST:
            case BlockFace.WEST:
               return MiningPlane.ZY;
            case BlockFace.UP:
            case BlockFace.DOWN:
               return MiningPlane.XZ;
         }
      } catch (Exception e) {
         HytaleLogger.getLogger().atWarning().withCause(e).log("Error calculating mining plane");
      }

      return MiningPlane.XY;
   }

   // Packet logic is hardcoded in Interaction.java
   
   // protected com.hypixel.hytale.protocol.Interaction generatePacket() {
   //    return new daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction();
   // }

   // protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
   //    super.configurePacket(packet);
   //    daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction p = (daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction)packet;
   //    p.radius = this.radius;
   //    p._offset = this.offset;
   // }

   public String toString() {
      return "BlockRadiusInteraction{radius=" + this.radius.toString() + ", offset=" + this.offset.toString() + "} " + super.toString();
   }

   public static final BuilderCodec<BlockRadiusInteraction> CODEC = BuilderCodec.builder(
         BlockRadiusInteraction.class, BlockRadiusInteraction::new, SimpleBlockInteraction.CODEC)
         .documentation("Runs a chain on surrounding blocks based on radius and face of targetted block")
         .append(new KeyedCodec<>("Radius", new Vector3iArrayCodec(), true), (o, radius) -> o.radius = radius, o -> o.radius)
         .documentation("The 3d block distance to apply interaction too.(3x3 would be [1,1,1])")
         .add()
         .append(new KeyedCodec<>("Offset", new Vector3iArrayCodec(), true), (o, offset) -> o.offset = offset, o -> o.offset)
         .documentation("Offset that changes where the center interaction block is.")
         .add()
         .<String>appendInherited(
               new KeyedCodec<>("ForBlock", RootInteraction.CHILD_ASSET_CODEC), (o, i) -> o.forBlock = i,
               o -> o.forBlock, (o, p) -> o.forBlock = p.forBlock)
         .documentation(
               "The interactions to fork into when a block is hit by the selector.\nThe hit block will be the target of the interaction chain.\n\nA block cannot be hit multiple times by a single selector.")
         .addValidatorLate(() -> RootInteraction.VALIDATOR_CACHE.getValidator().late())
         .add()
         .build();
}
