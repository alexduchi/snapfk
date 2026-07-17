package fr.neo.speedsmp;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.*;
import java.util.*;

public final class SpeedSmpPlugin extends JavaPlugin implements Listener, CommandExecutor {
    static final boolean DROP_ONLY_PLAYER_KILL = true;
    static final boolean GLOBAL_TRACKER = false;
    static final String FLAVOR = "PvP Kill";

    private static final int MIN = -3;
    private static final int MAX = 3;
    private static final int SWORD_SPEED_DURATION_TICKS = 200;
    private static final int COMBO_TIMEOUT_MS = 3500;
    private