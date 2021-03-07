package xyz.xmit.Darude;

import net.minecraft.server.v1_16_R3.Tuple;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Entity;
import xyz.xmit.StormWatch.Storm;
import xyz.xmit.StormWatch.StormConfig;

import java.util.Arrays;
import java.util.HashMap;


// KEYWORDS:
//   - "Cycle": one count of X [configured] server ticks after which to add the next yaw from the range.

public class StormSandstorm extends Storm {
    public static final String TYPE_NAME = "sandstorm";
    public static final Biome[] APPROVED_BIOMES = {
            Biome.DESERT, Biome.DESERT_HILLS, Biome.DESERT_LAKES,
            Biome.BADLANDS, Biome.BADLANDS_PLATEAU, Biome.ERODED_BADLANDS
    };
    public enum StormSandstormConfigurationKeyNames implements StormConfig.ConfigKeySet {
        SANDSTORM_WHIRL_RATE_RANGE("orientation.rotationDegreesRangePerCycle"), //how many degrees of yaw will the storm's rotation change per spawn (call to a scheduled spawning job)?
        ////SANDSTORM_CYCLE_TICK_COUNT("orientation.ticksPerCycle"), //how often does the yaw update for the storm?
        SANDSTORM_YAW_DITHER("orientation.yawDitherDegrees"), //how much "randomness" can be in the yaw for the current direction of the rotation
        SANDSTORM_ORIGIN_SPEED_FACTOR_RANGE("storm.originMovementSpeedRange"), //how quickly does the center of the storm move around the XZ axes
        SANDSTORM_STRENGTH_RANGE("storm.strengthFactorRange"), //how strong can the storms be (this one is a per-storm multiplier)
        SANDSTORM_PLAYER_IS_WARNED("storm.warnPlayer"), //whether or not to enable player warnings
        SANDSTORM_PLAYER_WARNING("storm.playerWarningMessage"), //message to send the target player
        ;
        public final String label;
        StormSandstormConfigurationKeyNames(String keyText) { this.label = keyText; }
        public final String getLabel() { return this.label; }
    }
    private static final HashMap<String, Object> defaultConfig = new HashMap<>() {{
        // Plugin-specific defaults.
        put(StormSandstormConfigurationKeyNames.SANDSTORM_WHIRL_RATE_RANGE.label, new int[]{10, 17});
        ////put(StormSandstormConfigurationKeyNames.SANDSTORM_CYCLE_TICK_COUNT.label, 4); //seems same as SPAWN_RATE
        put(StormSandstormConfigurationKeyNames.SANDSTORM_YAW_DITHER.label, 20);
        put(StormSandstormConfigurationKeyNames.SANDSTORM_ORIGIN_SPEED_FACTOR_RANGE.label, new double[]{0.7, 1.75});
        put(StormSandstormConfigurationKeyNames.SANDSTORM_STRENGTH_RANGE.label, new double[]{0.85, 1.15});
        put(StormSandstormConfigurationKeyNames.SANDSTORM_PLAYER_IS_WARNED.label, true);
        put(StormSandstormConfigurationKeyNames.SANDSTORM_PLAYER_WARNING.label, "The hot desert winds howl as a storm approaches.");
        ////put(StormSandstormConfigurationKeyNames.SANDSTORM_BLOCK_VELOCITY_FACTOR_RANGE.label, new double[]{0.7, 1.35});
        //// ^^^ This is just the "SPEED" parameter; use that instead.
        // Storm class required base configuration settings.
        put(RequiredConfigurationKeyNames.ENABLED.label, true);
        put(RequiredConfigurationKeyNames.ENVIRONMENTS.label, new String[]{"NORMAL"});
        put(RequiredConfigurationKeyNames.ENVIRONMENTS_ENFORCED.label, true);
        put(RequiredConfigurationKeyNames.CHANCE.label, 0.00333);
        put(RequiredConfigurationKeyNames.COOLDOWN_RANGE.label, new int[]{600,1500});
        put(RequiredConfigurationKeyNames.COOLDOWN_ENABLED.label, true);
        put(RequiredConfigurationKeyNames.DURATION_RANGE.label, new int[]{60,600});
        put(RequiredConfigurationKeyNames.FOLLOW_PLAYER.label, false);
        put(RequiredConfigurationKeyNames.SPEED_RANGE.label, new double[]{0.7,1.35});
        put(RequiredConfigurationKeyNames.TIME_RANGE.label, new int[]{0,24000});
        put(RequiredConfigurationKeyNames.TIME_RANGE_ENFORCED.label, false);
        put(RequiredConfigurationKeyNames.PITCH_RANGE.label, new int[]{-10,-60});
        put(RequiredConfigurationKeyNames.YAW_RANGE.label, new int[]{0,360});
        put(RequiredConfigurationKeyNames.SPAWN_RATE_RANGE.label, new int[]{0,5});
        put(RequiredConfigurationKeyNames.SPAWN_AMOUNT_RANGE.label, new int[]{5,15});
        put(RequiredConfigurationKeyNames.X_RANGE.label, new int[]{-100,100});
        put(RequiredConfigurationKeyNames.Z_RANGE.label, new int[]{-100,100});
        put(RequiredConfigurationKeyNames.HEIGHT_RANGE.label, new int[]{0,1});
        put(RequiredConfigurationKeyNames.WINDY.label, false);
        put(RequiredConfigurationKeyNames.WINDY_CHANCE.label, 0.001);
    }};

    // Trackers.
    private Location eyeLocation;
    // Configuration-provided.
    private boolean isPlayerWarned;
    private double stormStrength, originMovementSpeed;
    private int yawDither;
    private String customPlayerWarningText;
    private Tuple<Integer,Integer> whirlChangeRateRange;

    // Generic constructor.
    public StormSandstorm() { super(StormSandstorm.TYPE_NAME, StormSandstorm.defaultConfig); }

    // GET/SET methods, where applicable.
    public final boolean isPlayerWarned() { return this.isPlayerWarned; }
    public final double getStormStrength() { return this.stormStrength; }
    public final double getOriginMovementSpeed() { return this.originMovementSpeed; }
    public final int getYawDither() { return this.yawDither; }
    public final String getCustomPlayerWarningText() { return this.customPlayerWarningText; }
    public final Tuple<Integer,Integer> getWhirlChangeRateRange() { return this.whirlChangeRateRange; }
    public final Location getEyeLocation() { return this.eyeLocation; }


    // Get the next tick's storm "eye" location. This works on a few factors:
    //   - Is the next block still in an approved biome? No? Choose another direction (i.e. another attempt).
    //   - For the chosen XZ point, is this the lowest elevation with at least 30 blocks of "air" in the column above it?
    // If gotten next-locations fail this check more than 5 times, the storm dies.
    private Location getNextValidOriginLocation(Location relativeLocation) {
        for(int attempt = 0; attempt < 15; attempt++) {
            // If this.eyeLocation is still null, this would be the first run of the loop. Select a random start place.
            //   Otherwise, it's a relative movement from the previous eye location according to the movement speed factor.
            double xSpawn = relativeLocation.getX() + (
                    this.eyeLocation == null
                        ? this.getNewXSpawn()
                        : (this.getRandomDouble(-2.3,2.3) * this.originMovementSpeed)
            );
            double zSpawn = relativeLocation.getZ() + (
                    this.eyeLocation == null
                        ? this.getNewZSpawn()
                        : (this.getRandomDouble(-2.3,2.3) * this.originMovementSpeed)
            );
            double ySpawn = 0; //start from the lowest world elevation
            Location l = new Location(this.getTargetPlayer().getWorld(), xSpawn, ySpawn, zSpawn);

            // If the block isn't in the right biome, skip. Biomes don't change vertically, so this is OK to check here.
            if(!Arrays.asList(APPROVED_BIOMES).contains(l.getBlock().getBiome())) { continue; }

            // Get the lowest height with at least 30 blocks of air in the column above it at the randomized location.
            boolean originLocated = false;
            height_loop:
            for(int i = 2; i < this.getTargetPlayer().getWorld().getMaxHeight(); i++) {
                l.setY(i);
                if(l.getBlock().getBlockData().getMaterial() == Material.AIR) {
                    for(int j = 1; j < 31; j++) {
                        if(l.getBlock().getBlockData().getMaterial() != Material.AIR) {
                            i += j;
                            continue height_loop;
                        }
                    }
                    // If code reaches this area, the column is clear and the Y location is already set.
                    originLocated = true; break;
                }
            }
            if(!originLocated) { continue; }
            //should check if the spawn location is also over sand??
            return l;
        }
        return null;
    }

    @Override
    public final boolean stormSpecificConditionChecks() {
        return true;
    }

    @Override
    protected final boolean initializeStormTypeProperties() {
        Tuple<Double, Double> stormStrengthRange, originMovementSpeedRange;
        try {
            this.whirlChangeRateRange =
                    StormConfig.getIntegerRange(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_WHIRL_RATE_RANGE);
            this.isPlayerWarned =
                    StormConfig.getConfigValue(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_PLAYER_IS_WARNED);
            this.customPlayerWarningText =
                    StormConfig.getConfigValue(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_PLAYER_WARNING);
            this.yawDither =
                    StormConfig.getConfigValue(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_YAW_DITHER);
            stormStrengthRange =
                    StormConfig.getDoubleRange(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_STRENGTH_RANGE);
            originMovementSpeedRange =
                    StormConfig.getDoubleRange(this.typeName, StormSandstormConfigurationKeyNames.SANDSTORM_ORIGIN_SPEED_FACTOR_RANGE);
        } catch (Exception ex) {
            this.log(ex);
            return false;
        }
        // Make sure the storm is spawning in the right biome(s), and set an appropriate "eye" location in a place without a ceiling.
        //   This is actually where the initial eye location would be set.
        Location eyeStart = this.getNextValidOriginLocation(this.getBaseSpawnLocation());
        if(eyeStart == null) {
            this.debugLog("Sandstorm couldn't find a habitable origin to start up from. Cancelling event.");
            this.setCancelled(true);
            return true;   //technically should return true since this isn't a config or property failure
        } else { this.eyeLocation = eyeStart; }
        // Finally, set the remaining properties.
        this.stormStrength = this.getRandomDouble(stormStrengthRange);
        this.originMovementSpeed = this.getRandomDouble(originMovementSpeedRange);
        return true;
    }

    @Override
    protected final void doJustBeforeScheduling() {
        // Always disable following the player -- this storm has its own origin and TENDS toward the player.
        this.setFollowPlayer(false);
        // Force the "spawn amount" value to be ignored completely in the super-class scheduler so it can be used in `getNextEntity`.
        // NOTE: This is different from the "impact" event because we still want the scheduler's timer operable to "schedule" the
        //        rotation of the storm's origin (and thus an update to the storm's YAW value).
        this.setSingleSpawnPerJob(true);
    }

    @Override
    protected final void doJustAfterScheduling() {
        // Send an ominous message to the player that a sandstorm is spawning within their vicinity.
        if(this.isPlayerWarned()) { this.getTargetPlayer().sendMessage(this.customPlayerWarningText); }
    }

    @Override
    protected final Entity getNextEntity() {
        // Get a new relative location.
        this.eyeLocation = this.getNextValidOriginLocation(this.eyeLocation);
        if(this.eyeLocation == null) {
            this.log("Sandstorm died prematurely since it ran out of locations for the origin to move to.");
            return null;
        }

        // Get some amount of sand blocks to spawn and get to work.
        int spawnedEntities = this.getRandomInt(this.getSpawnAmountRange());
        for(int i = 0; i < spawnedEntities; i++) {
            Location blockLoc = this.eyeLocation.clone();
            blockLoc.setPitch(this.getNewPitch());
            //// The yaw is based on the current storm "whirl" plus a value from 0 to the dithering threshold.
            blockLoc.setYaw((this.getStormYaw() + this.getRandomInt(0, this.yawDither)) % 360);
            //// Spawn the block and set its trajectory.
            Entity e = blockLoc.getWorld().spawnFallingBlock(blockLoc, Material.SAND.createBlockData());
            e.setVelocity(blockLoc.getDirection().multiply(this.getNewSpeed()));
            this.addSpawnedEntity(e); //track the spawned block (why not)
        }

        // Set a new storm yaw incrementally higher based on the whirl change rate.
        this.setStormYaw(this.getStormYaw() + this.getRandomInt(this.whirlChangeRateRange));

        // No entities need to be returned with this storm type.
        return null;
    }

    @Override
    public final void doCleanupAfterStorm() {

    }
}
