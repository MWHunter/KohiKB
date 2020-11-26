package defineoutside.pvp;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;

public class KohiKB extends JavaPlugin implements Listener, CommandExecutor {
    double knockbackHorizontal = 0.35D;
    double knockbackVertical = 0.35D;
    double knockbackVerticalLimit = 0.4D;
    double knockbackExtraHorizontal = 0.425D;
    double knockbackExtraVertical = 0.085D;

    HashMap<Integer, Vector> velocityMap = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("kohikb").setExecutor(this);

        saveDefaultConfig();

        getConfigValues();

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(
            new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_VELOCITY) {

                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
                        PacketContainer packet = event.getPacket();
                        Integer messageInt = packet.getIntegers().read(0);

                        if (velocityMap.containsKey(messageInt)) {
                            Vector newVector = velocityMap.get(messageInt);
                            velocityMap.remove(messageInt);

                            // Velocity is believed to be in units of 1/8000 of a block per server tick
                            // (50ms); for example, -1343 would move (-1343 / 8000) = -0.167875 blocks per
                            // tick (or -3,3575 blocks per second).
                            //
                            // Source: https://wiki.vg/Protocol#Entity_Velocity

                            packet.getIntegers().write(1, (int) (newVector.getX() * 8000))
                                .write(2, (int) (newVector.getY() * 8000))
                                .write(3, (int) (newVector.getZ() * 8000));
                        }
                    }
                }
            });
    }

    private void getConfigValues() {
        knockbackHorizontal = getConfig().getDouble("knockbackHorizontal");
        knockbackVertical = getConfig().getDouble("knockbackVertical");
        knockbackVerticalLimit = getConfig().getDouble("knockbackVerticalLimit");
        knockbackExtraHorizontal = getConfig().getDouble("knockbackExtraHorizontal");
        knockbackExtraVertical = getConfig().getDouble("knockbackExtraVertical");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        // Check if sword PvP, not PvE or EvE
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player && !event.isCancelled() && event.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK)) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            // Figure out base knockback direction
            double d1 = attacker.getLocation().getX() - victim.getLocation().getX();
            double d0;

            for (d0 = attacker.getLocation().getZ() - victim.getLocation().getZ();
                 d1 * d1 + d0 * d0 < 1.0E-4D; d0 = (Math.random() - Math.random()) * 0.01D)
            {
                d1 = (Math.random() - Math.random()) * 0.01D;
            }

            double magnitude = Math.sqrt(d1 * d1 + d0 * d0);

            // Do base knockback
            Vector currentVelocity = victim.getVelocity();
            currentVelocity.setX(currentVelocity.getX() - (d1 / magnitude * knockbackHorizontal));
            currentVelocity.setY(currentVelocity.getY() + knockbackVertical);
            currentVelocity.setZ(currentVelocity.getZ() - (d0 / magnitude * knockbackHorizontal));

            if (currentVelocity.getY() > knockbackVerticalLimit) {
                currentVelocity.setY(knockbackVerticalLimit);
            }


            event.getEntity().setVelocity(currentVelocity);

            // Calculate bonus for sprinting or enchantments
            int i = attacker.getItemInHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (attacker.isSprinting()) ++i;

            // Run bonus knockback
            Vector damageVelocity = new Vector((-Math.sin(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                (float) i * knockbackExtraHorizontal), knockbackExtraVertical,
                Math.cos(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) * (float) i * knockbackExtraHorizontal);

            if (damageVelocity.getY() > knockbackVerticalLimit) {
                damageVelocity.setY(knockbackVerticalLimit);
            }

            event.getEntity().setVelocity(event.getEntity().getVelocity().add(damageVelocity));


            // Server side velocity is useless but set it anyways to help anticheats
            velocityMap.put(event.getEntity().getEntityId(), event.getEntity().getVelocity());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("kohikb") && sender.hasPermission("kohikb.reload") &&
            args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            getConfigValues();
            sender.sendMessage(ChatColor.AQUA + "You have reloaded the config");
            return true;
        }

        return false;
    }
}
