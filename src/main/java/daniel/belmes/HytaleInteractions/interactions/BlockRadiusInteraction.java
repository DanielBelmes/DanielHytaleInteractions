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
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
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
   public static final MetaKey<Set<BlockPosition>> FOR_BLOCKS = META_REGISTRY.registerMetaObject(i -> new HashSet<>());
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
            BlockFace face = context.getClientState().blockFace;
            MiningPlane plane = getMiningPlane(face);

            DynamicMetaStore<Interaction> instanceStore = context.getInstanceStore();
            Set<BlockPosition> forBlocks = (Set) instanceStore.getMetaObject(FOR_BLOCKS);
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
      for (int d1 = -radius.x; d1 <= radius.x; d1++) {
         for (int d2 = -radius.y; d2 <= radius.y; d2++) {
            for (int d3 = -radius.z; d3 <= radius.z; d3++) {
               switch (plane) {
                  case XZ:
                     consumer.accept(d1 + targetBlock.x + offset.x, d3 + targetBlock.y + offset.z,
                           d2 + targetBlock.z + offset.y);
                     break;
                  case XY:
                     consumer.accept(d1 + targetBlock.x + offset.x, d2 + targetBlock.y + offset.y,
                           d3 + targetBlock.z + offset.z);
                     break;
                  case ZY:
                     consumer.accept(d3 + targetBlock.x + offset.z, d1 + targetBlock.y + offset.x,
                           d2 + targetBlock.z + offset.y);
                     break;
                  default:
                     continue;
               }
            }
         }
      }
   }

   private static MiningPlane getMiningPlane(BlockFace face) {
      if (face == null) {
         return MiningPlane.XY;
      }

      switch (face) {
         case BlockFace.North:
         case BlockFace.South:
            return MiningPlane.XY;
         case BlockFace.East:
         case BlockFace.West:
            return MiningPlane.ZY;
         case BlockFace.Up:
         case BlockFace.Down:
            return MiningPlane.XZ;
         default:
            return MiningPlane.XY;
      }
   }

   // Packet logic is hardcoded in Interaction.java

   // protected com.hypixel.hytale.protocol.Interaction generatePacket() {
   // return new
   // daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction();
   // }

   // protected void configurePacket(com.hypixel.hytale.protocol.Interaction
   // packet) {
   // super.configurePacket(packet);
   // daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction p =
   // (daniel.belmes.hytaleinteractions.protocol.BlockRadiusInteraction)packet;
   // p.radius = this.radius;
   // p._offset = this.offset;
   // }

   public String toString() {
      return "BlockRadiusInteraction{radius=" + this.radius.toString() + ", offset=" + this.offset.toString() + "} "
            + super.toString();
   }

   public static final BuilderCodec<BlockRadiusInteraction> CODEC = BuilderCodec.builder(
         BlockRadiusInteraction.class, BlockRadiusInteraction::new, SimpleBlockInteraction.CODEC)
         .documentation("Runs a chain on surrounding blocks based on radius and face of targetted block")
         .append(new KeyedCodec<>("Radius", new Vector3iArrayCodec(), true), (o, radius) -> o.radius = radius,
               o -> o.radius)
         .documentation("The 3d block distance to apply interaction too.(3x3x1 would be [1,1,0])")
         .add()
         .append(new KeyedCodec<>("Offset", new Vector3iArrayCodec(), true), (o, offset) -> o.offset = offset,
               o -> o.offset)
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
