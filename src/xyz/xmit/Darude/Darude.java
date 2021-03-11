package xyz.xmit.Darude;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.xmit.StormWatch.StormWatch;
//import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.logging.Level;

@SuppressWarnings("unused")
public class Darude extends JavaPlugin {
    public static Darude instance;
    public Darude() { Darude.instance = this; }

    @Override
    public void onEnable() {
        if(this.getServer().getPluginManager().getPlugin("StormWatch") != null) {
            StormWatch.getStormManager().registerNewStormType(StormSandstorm.class);
        } else { this.getLogger().log(Level.WARNING, "Could not register the StormSandstorm class for StormWatch!"); }
        // DE-REGISTRATION TESTING ONLY.
        /*var c = new BukkitRunnable() {
            @Override public void run() {
                Darude.instance.getServer().getPluginManager().disablePlugin(Darude.instance);
            }
        }.runTaskLater(this, 400L);*/
    }

    @Override
    public void onDisable() {
        try {
            if (Objects.requireNonNull(this.getServer().getPluginManager().getPlugin("StormWatch")).isEnabled()
                    && StormWatch.getStormManager() != null) {
                StormWatch.getStormManager().unregisterStormType(StormSandstorm.class);
            }
        } catch (Exception ex) {
            try {
                if (this.getServer().getPluginManager().getPlugin("StormWatch") != null
                        && Objects.requireNonNull(this.getServer().getPluginManager()
                        .getPlugin("StormWatch")).isEnabled()
                        && StormWatch.getStormManager().getRegisteredStormTypes() != null) {
                    this.getLogger().log(Level.WARNING,
                            "Tried to unregister from an enabled StormWatch instance but failed. THIS IS A PROBLEM!");
                    ex.printStackTrace();
                }
            } catch(Exception exc) {
                this.getLogger().log(Level.WARNING, "Fatal issue; cascading exceptions!");
                exc.printStackTrace();
            }
        }
    }
}
