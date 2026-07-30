package com.horrortricks.misplacedambience;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "misplacedambience")
public final class MisplacedAmbienceEvents {
   private static final int DEBUG_COMMAND_PERMISSION_LEVEL = 0;
   private static final int SEARCH_ABOVE = 2;
   private static final int SEARCH_BELOW = 8;
   private static final int SURVEY_VERTICAL_RANGE = 6;
   private static final double BASE_CANDIDATE_WEIGHT = 1.0;
   private static final double BEHIND_WEIGHT_BONUS_SCALE = 3.0;
   private static final double UNSEEN_WEIGHT_BONUS = 3.0;
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<UUID, Long> NEXT_TRIGGER_GAME_TIME = new HashMap<>();

   private MisplacedAmbienceEvents() {
   }

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel level && (Boolean)MisplacedAmbienceConfig.ENABLED.get()) {
         long gameTime = level.getGameTime();

         for (ServerPlayer player : level.players()) {
            processTimer(level, player, gameTime);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      NEXT_TRIGGER_GAME_TIME.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      register(event.getDispatcher());
   }

   private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("misplacedambience").requires(source -> source.hasPermission(0)))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("trigger").executes(context -> triggerNow((CommandSourceStack)context.getSource(), null)))
                  .then(Commands.argument("sound", StringArgumentType.word()).suggests((context, builder) -> {
                     for (MisplacedAmbienceSound sound : MisplacedAmbienceSound.values()) {
                        builder.suggest(sound.configKey());
                     }

                     return builder.buildFuture();
                  }).executes(context -> triggerNow((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "sound"))))
            )
      );
   }

   private static int triggerNow(CommandSourceStack source, @Nullable String soundKey) {
      if (source.getEntity() instanceof ServerPlayer player) {
         MisplacedAmbienceSound var5 = null;
         if (soundKey != null) {
            var5 = findByConfigKey(soundKey);
            if (var5 == null) {
               LOGGER.info("[misplacedambience debug] Unknown sound '{}'.", soundKey);
               return 0;
            }
         }

         boolean played = triggerAmbience(player.serverLevel(), player, var5);
         LOGGER.info(
            "[misplacedambience debug] {} for {}.",
            played ? "Played a misplaced ambience sound" : "Nothing played (no valid position, or nothing eligible)",
            player.getGameProfile().getName()
         );
         return played ? 1 : 0;
      } else {
         LOGGER.info("[misplacedambience debug] /misplacedambience trigger must be run by a player.");
         return 0;
      }
   }

   private static void processTimer(ServerLevel level, ServerPlayer player, long gameTime) {
      UUID id = player.getUUID();
      Long next = NEXT_TRIGGER_GAME_TIME.get(id);
      if (next == null) {
         scheduleNext(id, gameTime, player.getRandom());
      } else if (gameTime >= next) {
         triggerAmbience(level, player, null);
         scheduleNext(id, gameTime, player.getRandom());
      }
   }

   private static void scheduleNext(UUID id, long gameTime, RandomSource random) {
      int min = (Integer)MisplacedAmbienceConfig.MIN_INTERVAL_TICKS.get();
      int max = Math.max(min, (Integer)MisplacedAmbienceConfig.MAX_INTERVAL_TICKS.get());
      int delay = min + random.nextInt(max - min + 1);
      NEXT_TRIGGER_GAME_TIME.put(id, gameTime + delay);
   }

   private static boolean triggerAmbience(ServerLevel level, ServerPlayer player, @Nullable MisplacedAmbienceSound forcedSound) {
      RandomSource random = player.getRandom();
      if (forcedSound != null) {
         BlockPos pos = null;
         if (forcedSound.requiresNearbyBlock()) {
            List<BlockPos> realMatches = surveyBlocks(level, player).get(forcedSound);
            if (realMatches != null && !realMatches.isEmpty()) {
               pos = realMatches.get(random.nextInt(realMatches.size()));
            }
         }

         if (pos == null) {
            pos = findRandomValidPos(level, player, random);
         }

         if (pos == null) {
            return false;
         }

         playSoundAt(level, random, forcedSound, pos);
         return true;
      } else {
         List<MisplacedAmbienceEvents.Candidate> candidates = buildCandidates(level, player, random);
         if (candidates.isEmpty()) {
            return false;
         }

         MisplacedAmbienceEvents.Candidate chosen = pickWeightedCandidate(level, player, candidates, random);
         playSoundAt(level, random, chosen.sound(), chosen.pos());
         return true;
      }
   }

   private static List<MisplacedAmbienceEvents.Candidate> buildCandidates(ServerLevel level, ServerPlayer player, RandomSource random) {
      List<MisplacedAmbienceEvents.Candidate> candidates = new ArrayList<>();
      if (anyBlockSoundEnabled()) {
         for (Entry<MisplacedAmbienceSound, List<BlockPos>> entry : surveyBlocks(level, player).entrySet()) {
            for (BlockPos pos : entry.getValue()) {
               candidates.add(new MisplacedAmbienceEvents.Candidate(entry.getKey(), pos));
            }
         }
      }

      if (isSoundEnabled(MisplacedAmbienceSound.CAVE_AMBIENCE) && isCaveLike(level, player)) {
         addRandomPositionCandidate(candidates, level, player, random, MisplacedAmbienceSound.CAVE_AMBIENCE);
      }

      if (isSoundEnabled(MisplacedAmbienceSound.WATER_DRIP) && isWaterDripLike(level, player)) {
         addRandomPositionCandidate(candidates, level, player, random, MisplacedAmbienceSound.WATER_DRIP);
      }

      if (isSoundEnabled(MisplacedAmbienceSound.ARMOR_STAND_CREAK)) {
         for (BlockPos pos : findNearbyArmorStands(level, player)) {
            candidates.add(new MisplacedAmbienceEvents.Candidate(MisplacedAmbienceSound.ARMOR_STAND_CREAK, pos));
         }
      }

      if (isSoundEnabled(MisplacedAmbienceSound.DISTANT_MINING)) {
         BlockPos pos = findDistantMiningPos(level, player);
         if (pos != null) {
            candidates.add(new MisplacedAmbienceEvents.Candidate(MisplacedAmbienceSound.DISTANT_MINING, pos));
         }
      }

      return candidates;
   }

   private static boolean isSoundEnabled(MisplacedAmbienceSound sound) {
      return (Boolean)MisplacedAmbienceConfig.SOUND_ENABLED.get(sound).get();
   }

   private static void addRandomPositionCandidate(
      List<MisplacedAmbienceEvents.Candidate> candidates, ServerLevel level, ServerPlayer player, RandomSource random, MisplacedAmbienceSound sound
   ) {
      BlockPos pos = findRandomValidPos(level, player, random);
      if (pos != null) {
         candidates.add(new MisplacedAmbienceEvents.Candidate(sound, pos));
      }
   }

   private static MisplacedAmbienceEvents.Candidate pickWeightedCandidate(
      ServerLevel level, ServerPlayer player, List<MisplacedAmbienceEvents.Candidate> candidates, RandomSource random
   ) {
      Vec3 look = player.getLookAngle();
      double lookHorizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
      Vec3 eyePos = player.getEyePosition();
      double biasStrength = (Double)MisplacedAmbienceConfig.UNSEEN_SOURCE_BIAS_STRENGTH.get();
      double[] weights = new double[candidates.size()];
      double total = 0.0;

      for (int i = 0; i < candidates.size(); i++) {
         MisplacedAmbienceEvents.Candidate candidate = candidates.get(i);
         BlockPos pos = candidate.pos();
         double dx = pos.getX() + 0.5 - player.getX();
         double dz = pos.getZ() + 0.5 - player.getZ();
         double horizontalLength = Math.sqrt(dx * dx + dz * dz);
         double behindness = 0.0;
         if (horizontalLength > 1.0E-4 && lookHorizontalLength > 1.0E-4) {
            double dot = (dx * look.x + dz * look.z) / (horizontalLength * lookHorizontalLength);
            behindness = -dot;
         }

         boolean inFrontHemisphere = behindness < 0.0;
         boolean unseen;
         if (inFrontHemisphere) {
            Vec3 candidateEyePos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            unseen = !hasLineOfSight(level, eyePos, candidateEyePos);
         } else {
            unseen = true;
         }

         double behindBonus = Math.max(0.0, behindness) * 3.0;
         double unseenBonus = unseen ? 3.0 : 0.0;
         double baseWeight = candidate.sound() == MisplacedAmbienceSound.DISTANT_MINING ? (Double)MisplacedAmbienceConfig.DISTANT_MINING_WEIGHT.get() : 1.0;
         double weight = Math.max(baseWeight * (1.0 + biasStrength * (behindBonus + unseenBonus)), 0.01);
         weights[i] = weight;
         total += weight;
      }

      double roll = random.nextDouble() * total;
      double accumulated = 0.0;

      for (int i = 0; i < candidates.size(); i++) {
         accumulated += weights[i];
         if (roll < accumulated) {
            return candidates.get(i);
         }
      }

      return candidates.get(candidates.size() - 1);
   }

   private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
      BlockHitResult result = level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
      return result.getType() == Type.MISS;
   }

   private static boolean isWaterDripLike(ServerLevel level, ServerPlayer player) {
      BlockPos pos = player.blockPosition();
      return !level.canSeeSky(pos) && level.getBrightness(LightLayer.SKY, pos) <= (Integer)MisplacedAmbienceConfig.WATER_DRIP_MAX_SKY_LIGHT.get();
   }

   private static List<BlockPos> findNearbyArmorStands(ServerLevel level, ServerPlayer player) {
      double radius = Math.max((Double)MisplacedAmbienceConfig.MIN_RADIUS.get(), (Double)MisplacedAmbienceConfig.MAX_RADIUS.get());
      AABB area = player.getBoundingBox().inflate(radius);
      List<BlockPos> positions = new ArrayList<>();

      for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area)) {
         positions.add(stand.blockPosition());
      }

      return positions;
   }

   @Nullable
   private static BlockPos findDistantMiningPos(ServerLevel level, ServerPlayer player) {
      BlockPos anchor = player.getRespawnPosition();
      ResourceKey<Level> anchorDimension = player.getRespawnDimension();
      if (anchor != null && anchorDimension == level.dimension()) {
         double distance = Math.sqrt(player.blockPosition().distSqr(anchor));
         if (distance < (Double)MisplacedAmbienceConfig.DISTANT_MINING_MIN_DISTANCE.get()) {
            return null;
         }

         double dx = anchor.getX() - player.getX();
         double dz = anchor.getZ() - player.getZ();
         double horizontalLength = Math.sqrt(dx * dx + dz * dz);
         if (horizontalLength < 1.0E-4) {
            return null;
         }

         double minRadius = (Double)MisplacedAmbienceConfig.MIN_RADIUS.get();
         double maxRadius = Math.max(minRadius, (Double)MisplacedAmbienceConfig.MAX_RADIUS.get());
         double distanceOut = minRadius + player.getRandom().nextDouble() * (maxRadius - minRadius);
         int x = (int)Math.floor(player.getX() + dx / horizontalLength * distanceOut);
         int z = (int)Math.floor(player.getZ() + dz / horizontalLength * distanceOut);
         return findValidPos(level, x, player.getBlockY(), z);
      } else {
         return null;
      }
   }

   private static Map<MisplacedAmbienceSound, List<BlockPos>> surveyBlocks(ServerLevel level, ServerPlayer player) {
      Map<MisplacedAmbienceSound, List<BlockPos>> matches = new EnumMap<>(MisplacedAmbienceSound.class);
      int radius = (int)Math.ceil(Math.max((Double)MisplacedAmbienceConfig.MIN_RADIUS.get(), (Double)MisplacedAmbienceConfig.MAX_RADIUS.get()));
      BlockPos center = player.blockPosition();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -6; dy <= 6; dy++) {
               cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               if (level.isInWorldBounds(cursor) && level.isLoaded(cursor)) {
                  BlockState state = level.getBlockState(cursor);

                  for (MisplacedAmbienceSound sound : MisplacedAmbienceSound.values()) {
                     if (sound.requiresNearbyBlock() && sound.matchesBlock(state)) {
                        matches.computeIfAbsent(sound, unused -> new ArrayList<>()).add(cursor.immutable());
                     }
                  }
               }
            }
         }
      }

      return matches;
   }

   private static boolean anyBlockSoundEnabled() {
      for (MisplacedAmbienceSound sound : MisplacedAmbienceSound.values()) {
         if (sound.requiresNearbyBlock() && (Boolean)MisplacedAmbienceConfig.SOUND_ENABLED.get(sound).get()) {
            return true;
         }
      }

      return false;
   }

   private static boolean isCaveLike(ServerLevel level, ServerPlayer player) {
      BlockPos pos = player.blockPosition();
      return !level.canSeeSky(pos) && pos.getY() <= (Integer)MisplacedAmbienceConfig.CAVE_AMBIENCE_MAX_Y.get();
   }

   @Nullable
   private static MisplacedAmbienceSound findByConfigKey(String key) {
      for (MisplacedAmbienceSound sound : MisplacedAmbienceSound.values()) {
         if (sound.configKey().equalsIgnoreCase(key)) {
            return sound;
         }
      }

      return null;
   }

   private static void playSoundAt(ServerLevel level, RandomSource random, MisplacedAmbienceSound sound, BlockPos pos) {
      float volume = (0.8F + random.nextFloat() * 0.2F) * ((Double)MisplacedAmbienceConfig.VOLUME.get()).floatValue();
      float pitch = 0.9F + random.nextFloat() * 0.2F;
      level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound.soundEvent(), SoundSource.AMBIENT, volume, pitch);
   }

   private static BlockPos findRandomValidPos(ServerLevel level, ServerPlayer player, RandomSource random) {
      double minRadius = (Double)MisplacedAmbienceConfig.MIN_RADIUS.get();
      double maxRadius = Math.max(minRadius, (Double)MisplacedAmbienceConfig.MAX_RADIUS.get());
      double angle = random.nextDouble() * Math.PI * 2.0;
      double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
      int x = (int)Math.floor(player.getX() + Math.cos(angle) * distance);
      int z = (int)Math.floor(player.getZ() + Math.sin(angle) * distance);
      int baseY = player.getBlockY();
      return findValidPos(level, x, baseY, z);
   }

   private static BlockPos findValidPos(ServerLevel level, int x, int baseY, int z) {
      for (int dy = 2; dy >= -8; dy--) {
         BlockPos pos = new BlockPos(x, baseY + dy, z);
         if (level.isInWorldBounds(pos) && level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()) {
               return pos;
            }
         }
      }

      return null;
   }

   private record Candidate(MisplacedAmbienceSound sound, BlockPos pos) {
   }
}
