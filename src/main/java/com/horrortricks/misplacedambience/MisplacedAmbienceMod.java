package com.horrortricks.misplacedambience;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod("misplacedambience")
public class MisplacedAmbienceMod {
   public static final String MOD_ID = "misplacedambience";
   private static final Logger LOGGER = LogUtils.getLogger();

   public MisplacedAmbienceMod(IEventBus modEventBus, ModContainer modContainer) {
      modContainer.registerConfig(Type.COMMON, MisplacedAmbienceConfig.SPEC);
      LOGGER.info("Misplaced Ambience initialized.");
   }
}
