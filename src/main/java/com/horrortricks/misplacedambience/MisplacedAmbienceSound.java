package com.horrortricks.misplacedambience;

import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum MisplacedAmbienceSound {
   WOODEN_DOOR_OPEN("woodenDoorOpen", () -> SoundEvents.WOODEN_DOOR_OPEN, "a wooden door creaking open", state -> state.is(BlockTags.WOODEN_DOORS)),
   WOODEN_DOOR_CLOSE("woodenDoorClose", () -> SoundEvents.WOODEN_DOOR_CLOSE, "a wooden door swinging shut", state -> state.is(BlockTags.WOODEN_DOORS)),
   IRON_DOOR_OPEN("ironDoorOpen", () -> SoundEvents.IRON_DOOR_OPEN, "a heavy iron door grinding open", state -> state.is(Blocks.IRON_DOOR)),
   CHEST_OPEN("chestOpen", () -> SoundEvents.CHEST_OPEN, "a chest lid creaking open", state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)),
   CHEST_CLOSE("chestClose", () -> SoundEvents.CHEST_CLOSE, "a chest lid falling shut", state -> state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)),
   BARREL_OPEN("barrelOpen", () -> SoundEvents.BARREL_OPEN, "a barrel lid creaking open", state -> state.is(Blocks.BARREL)),
   WOODEN_TRAPDOOR_OPEN("woodenTrapdoorOpen", () -> SoundEvents.WOODEN_TRAPDOOR_OPEN, "a trapdoor creaking open", state -> state.is(BlockTags.WOODEN_TRAPDOORS)),
   FENCE_GATE_OPEN("fenceGateOpen", () -> SoundEvents.FENCE_GATE_OPEN, "an outdoor gate creaking open", state -> state.is(BlockTags.FENCE_GATES)),
   CAVE_AMBIENCE("caveAmbience", () -> (SoundEvent)SoundEvents.AMBIENT_CAVE.value(), "a distant cave ambience burst", null),
   LEVER_CLICK("leverClick", () -> SoundEvents.LEVER_CLICK, "a faint mechanical click, like a lever being thrown", state -> state.is(Blocks.LEVER)),
   BELL_RESONANCE("bellResonance", () -> SoundEvents.BELL_RESONATE, "a bell resonating on its own", state -> state.is(Blocks.BELL)),
   MINECART_RUMBLE("minecartRumble", () -> SoundEvents.MINECART_RIDING, "a faint minecart rumble along nearby rails", state -> state.is(BlockTags.RAILS)),
   ARMOR_STAND_CREAK("armorStandCreak", () -> SoundEvents.ARMOR_STAND_HIT, "an armor stand creaking as if it shifted", null),
   WATER_DRIP("waterDrip", () -> SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, "a faint water drip echoing in a cave", null),
   DISTANT_MINING("distantMining", () -> SoundEvents.STONE_BREAK, "faint, muffled mining sounds from the direction of your spawn anchor", null);

   private final String configKey;
   private final Supplier<SoundEvent> soundEvent;
   private final String description;
   @Nullable
   private final Predicate<BlockState> blockMatcher;

   MisplacedAmbienceSound(String configKey, Supplier<SoundEvent> soundEvent, String description, @Nullable Predicate<BlockState> blockMatcher) {
      this.configKey = configKey;
      this.soundEvent = soundEvent;
      this.description = description;
      this.blockMatcher = blockMatcher;
   }

   public String configKey() {
      return this.configKey;
   }

   public SoundEvent soundEvent() {
      return this.soundEvent.get();
   }

   public String description() {
      return this.description;
   }

   public boolean requiresNearbyBlock() {
      return this.blockMatcher != null;
   }

   public boolean matchesBlock(BlockState state) {
      return this.blockMatcher != null && this.blockMatcher.test(state);
   }
}
