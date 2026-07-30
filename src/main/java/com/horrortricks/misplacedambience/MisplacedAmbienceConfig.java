package com.horrortricks.misplacedambience;

import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class MisplacedAmbienceConfig {
   private static final Builder BUILDER = new Builder();
   public static final BooleanValue ENABLED = BUILDER.comment("Master switch for the misplaced ambience effect.").define("enabled", true);
   public static final IntValue MIN_INTERVAL_TICKS = BUILDER.comment(
         new String[]{"Minimum ticks (20 ticks/second) between misplaced ambient sounds for a given player.", "Default of 6000 is 5 minutes."}
      )
      .defineInRange("minIntervalTicks", 6000, 20, Integer.MAX_VALUE);
   public static final IntValue MAX_INTERVAL_TICKS = BUILDER.comment(
         new String[]{"Maximum ticks (20 ticks/second) between misplaced ambient sounds for a given player.", "Default of 24000 is 20 minutes."}
      )
      .defineInRange("maxIntervalTicks", 24000, 20, Integer.MAX_VALUE);
   public static final DoubleValue MIN_RADIUS = BUILDER.comment("Minimum horizontal distance (in blocks) from the player the sound can play at.")
      .defineInRange("minRadius", 4.0, 1.0, 64.0);
   public static final DoubleValue MAX_RADIUS = BUILDER.comment("Maximum horizontal distance (in blocks) from the player the sound can play at.")
      .defineInRange("maxRadius", 12.0, 1.0, 64.0);
   public static final DoubleValue VOLUME = BUILDER.comment("Volume multiplier applied on top of each sound's own default volume.")
      .defineInRange("volume", 1.0, 0.0, 5.0);
   public static final IntValue CAVE_AMBIENCE_MAX_Y = BUILDER.comment(
         new String[]{
            "The cave ambience burst is only eligible when the player has no sky access AND is at or",
            "below this Y level - default of 50 is a bit below sea level, well into typical cave depth,",
            "so it doesn't also trigger for someone merely standing indoors near the surface."
         }
      )
      .defineInRange("caveAmbienceMaxY", 50, -64, 320);
   public static final IntValue WATER_DRIP_MAX_SKY_LIGHT = BUILDER.comment(
         new String[]{
            "The water drip sound is only eligible when the player has no sky access AND the residual",
            "sky light level at their position is at or below this - default of 3 is quite dim, so it",
            "reads as a genuinely enclosed cave pocket rather than just under a thin roof."
         }
      )
      .defineInRange("waterDripMaxSkyLight", 3, 0, 15);
   public static final DoubleValue UNSEEN_SOURCE_BIAS_STRENGTH = BUILDER.comment(
         new String[]{
            "How strongly the chosen sound's position prefers being behind the player and out of their",
            "sight (line-of-sight blocked, or outside their general facing direction) over a uniformly",
            "random eligible position, when several plausible sources qualify at once. 0 is a pure",
            "uniform random choice among eligible sources (no bias); higher values increasingly favor",
            "behind/unseen ones - but it's always a bias, never a hard requirement."
         }
      )
      .defineInRange("unseenSourceBiasStrength", 1.0, 0.0, 5.0);
   public static final DoubleValue DISTANT_MINING_WEIGHT = BUILDER.comment(
         new String[]{
            "Relative selection weight for the distant mining sound compared to every other eligible",
            "sound (which each implicitly weigh 1.0) - kept low by default so it's a rare occurrence",
            "even when eligible, not a regular one. Only ever eligible at all if the player has a real",
            "respawn anchor (bed/respawn anchor) set in their current dimension."
         }
      )
      .defineInRange("distantMiningWeight", 0.15, 0.0, 5.0);
   public static final DoubleValue DISTANT_MINING_MIN_DISTANCE = BUILDER.comment(
         "How far (in blocks) the player must be from their respawn anchor for the distant mining sound to become eligible."
      )
      .defineInRange("distantMiningMinDistance", 100.0, 1.0, 10000.0);
   public static final Map<MisplacedAmbienceSound, BooleanValue> SOUND_ENABLED;
   public static final ModConfigSpec SPEC;

   private MisplacedAmbienceConfig() {
   }

   static {
      BUILDER.push("general");
      BUILDER.pop();
      BUILDER.push("sounds");
      Map<MisplacedAmbienceSound, BooleanValue> soundEnabled = new EnumMap<>(MisplacedAmbienceSound.class);

      for (MisplacedAmbienceSound sound : MisplacedAmbienceSound.values()) {
         soundEnabled.put(sound, BUILDER.comment("Whether " + sound.description() + " can be picked.").define(sound.configKey(), true));
      }

      SOUND_ENABLED = soundEnabled;
      BUILDER.pop();
      SPEC = BUILDER.build();
   }
}
