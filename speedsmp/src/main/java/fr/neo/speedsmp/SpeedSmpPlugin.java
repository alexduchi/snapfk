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
    private static final int MIN=-3, MAX=3, SPEED_TICKS=200;
    private static final long COMBO_MS=3500, STUN_IMMUNITY_MS=8000;
    private final Map<UUID,Integer> speed=new HashMap<>();
    private final Map<UUID,Combo> combos=new HashMap<>();
    private final Map<UUID,FxState> fx=new HashMap<>();
    private final Map<UUID,Long> stunImmuneUntil=new HashMap<>();
    private NamespacedKey fragmentKey,fragmentIdKey;
    private File dataFile;

    @Override public void onEnable(){
        fragmentKey=new NamespacedKey(this,"fragment"); fragmentIdKey=new NamespacedKey(this,"fragment_id");
        dataFile=new File(getDataFolder(),"players.yml"); load();
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("speed")).setExecutor(this);
        Objects.requireNonNull(getCommand("speeditem")).setExecutor(this);
        Objects.requireNonNull(getCommand("withdraw")).setExecutor(this);
        for(Player p:Bukkit.getOnlinePlayers())setupBoard(p);
        Bukkit.getScheduler().runTaskTimer(this,()->{for(Player p:Bukkit.getOnlinePlayers())refresh(p);rescueFragments();},10,10);
        getLogger().info("SpeedSMP 1.0.5 PvP Kill enabled.");
    }
    @Override public void onDisable(){save();for(UUID id:new ArrayList<>(fx.keySet()))stopFx(id);}

    @EventHandler public void join(PlayerJoinEvent e){setupBoard(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){combos.remove(e.getPlayer().getUniqueId());stopFx(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e){
        Player p=e.getEntity(); int old=getSpeed(p); setSpeed(p,old-1); combos.remove(p.getUniqueId()); stopFx(p.getUniqueId());
        if(old>MIN&&p.getKiller()!=null)spawnFragment(p.getLocation().add(0,.45,0),createFragment());
        refreshAll();
    }
    @EventHandler public void burn(EntityCombustEvent e){if(e.getEntity() instanceof Item i&&isFragment(i.getItemStack()))e.setCancelled(true);}
    @EventHandler public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Item i&&isFragment(i.getItemStack()))e.setCancelled(true);}
    @EventHandler public void despawn(ItemDespawnEvent e){if(isFragment(e.getEntity().getItemStack()))e.setCancelled(true);}
    @EventHandler public void merge(ItemMergeEvent e){if(isFragment(e.getEntity().getItemStack())||isFragment(e.getTarget().getItemStack()))e.setCancelled(true);}
    @EventHandler public void hopper(InventoryPickupItemEvent e){if(isFragment(e.getItem().getItemStack()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void playerDrop(PlayerDropItemEvent e){if(isFragment(e.getItemDrop().getItemStack()))protect(e.getItemDrop());}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity target))return;
        Material mat=p.getInventory().getItemInMainHand().getType();
        Weapon weapon=mat.name().endsWith("_SWORD")?Weapon.SWORD:mat.name().endsWith("_AXE")?Weapon.AXE:null;
        if(weapon==null){combos.remove(p.getUniqueId());return;}
        long now=System.currentTimeMillis(); Combo old=combos.get(p.getUniqueId());
        int hits=old!=null&&old.target().equals(target.getUniqueId())&&old.weapon()==weapon&&old.expires()>now?old.hits()+1:1;
        combos.put(p.getUniqueId(),new Combo(target.getUniqueId(),target.getName(),weapon,hits,now+COMBO_MS));
        if(weapon==Weapon.SWORD){
            int maxAmplifier=getSpeed(p)==MAX?4:2;
            int amplifier=Math.min(maxAmplifier,hits-1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,SPEED_TICKS,amplifier,true,false,true));
            if(getSpeed(p)==MAX&&hits>=4){e.setDamage(e.getDamage()*1.5);target.getWorld().spawnParticle(Particle.CRIT,target.getLocation().add(0,target.getHeight()/2,0),12,.3,.4,.3,.1);}
        }else{
            if(isCritical(p))target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,35,0,true,true,true));
            if(hits>=5){
                if(now>=stunImmuneUntil.getOrDefault(target.getUniqueId(),0L))freeze(target,getSpeed(p)==MAX?80:40);
                else p.sendActionBar("§7Cette cible resiste encore au gel.");
                combos.remove(p.getUniqueId());
            }
        }
        refresh(p);
    }
    private boolean isCritical(Player p){return p.getFallDistance()>0&&!p.isOnGround()&&!p.isInWater()&&!p.isClimbing()&&!p.hasPotionEffect(PotionEffectType.BLINDNESS);}
    private void freeze(LivingEntity entity,int ticks){
        boolean hadAi=entity instanceof Mob mob&&mob.hasAI(); if(entity instanceof Mob mob)mob.setAI(false);
        Location lock=entity.getLocation().clone(); BukkitTask[] task=new BukkitTask[1];
        task[0]=Bukkit.getScheduler().runTaskTimer(this,new Runnable(){int n;public void run(){
            if(!entity.isValid()||n++>=ticks){if(entity instanceof Mob mob)mob.setAI(hadAi);stunImmuneUntil.put(entity.getUniqueId(),System.currentTimeMillis()+STUN_IMMUNITY_MS);task[0].cancel();return;}
            entity.teleport(lock);entity.setVelocity(new Vector());entity.getWorld().spawnParticle(Particle.SNOWFLAKE,entity.getLocation().add(0,entity.getHeight()/2,0),3,.3,.5,.3,.01);
        }},0,1);
    }

    @EventHandler public void use(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return;
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;
        ItemStack item=e.getItem();if(!isFragment(item))return;e.setCancelled(true);Player p=e.getPlayer();
        if(getSpeed(p)>=MAX){p.sendMessage("§cTa Speed est deja au maximum.");return;}
        item.setAmount(item.getAmount()-1);int before=getSpeed(p),after=setSpeed(p,before+1);playFx(p,before,after);refreshAll();
    }

    private ItemStack createFragment(){
        ItemStack item=new ItemStack(Material.POTION);PotionMeta meta=(PotionMeta)item.getItemMeta();
        meta.setDisplayName("§b§l✦ FRAGMENT DE VELOCITE ✦");meta.setColor(Color.fromRGB(66,220,255));
        meta.setLore(List.of("§7Une essence instable arrachee a la vitesse","§7d'un combattant tombe au combat.","","§fClic droit pour absorber son energie.","","§b➜ §fAugmente ta Speed de §a+1","§b➜ §fNiveau maximal : §d+3","§b➜ §fObjet eternel et indestructible","","§8« La vitesse change simplement de maitre. »"));
        meta.getPersistentDataContainer().set(fragmentKey,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(fragmentIdKey,PersistentDataType.STRING,UUID.randomUUID().toString());
        meta.addEnchant(Enchantment.UNBREAKING,1,true);meta.addItemFlags(ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_ADDITIONAL_TOOLTIP);item.setItemMeta(meta);return item;
    }
    private boolean isFragment(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(fragmentKey,PersistentDataType.BYTE);}
    private Item spawnFragment(Location loc,ItemStack stack){Item item=loc.getWorld().dropItemNaturally(loc,stack);protect(item);return item;}
    private void protect(Item item){item.setUnlimitedLifetime(true);item.setInvulnerable(true);item.setPersistent(true);item.setCanMobPickup(false);item.setWillAge(false);}
    private void rescueFragments(){for(World w:Bukkit.getWorlds())for(Item i:w.getEntitiesByClass(Item.class))if(isFragment(i.getItemStack())){protect(i);if(i.getY()<w.getMinHeight()-8)i.teleport(w.getSpawnLocation().add(0,1,0));}}

    private void playFx(Player p,int before,int after){
        stopFx(p.getUniqueId());p.sendTitle("","§b✦ VELOCITE ABSORBEE ✦ §7("+before+" → §f"+after+"§7)",2,18,5);
        p.getWorld().strikeLightningEffect(p.getLocation());p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.75f,1.45f);p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,.55f,1.35f);
        FxState state=new FxState();Material[] mats={Material.POTION,Material.SUGAR,Material.FEATHER,Material.WIND_CHARGE,Material.AMETHYST_SHARD};
        for(int i=0;i<12;i++){ItemDisplay d=p.getWorld().spawn(p.getLocation().add(0,1.1,0),ItemDisplay.class);d.setItemStack(new ItemStack(mats[i%mats.length]));d.setPersistent(false);d.setInvulnerable(true);d.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),new Vector3f(.8f),new AxisAngle4f()));state.displays.add(d);}fx.put(p.getUniqueId(),state);
        state.task=Bukkit.getScheduler().runTaskTimer(this,new Runnable(){int n;public void run(){
            if(!p.isOnline()||n++>75||fx.get(p.getUniqueId())!=state){if(fx.get(p.getUniqueId())==state)stopFx(p.getUniqueId());else state.task.cancel();return;}
            double phase=n*.16;for(int i=0;i<state.displays.size();i++){ItemDisplay d=state.displays.get(i);if(!d.isValid())continue;double a=phase+i*(Math.PI*2/state.displays.size()),r=n<12?n/12.0*1.5:n>58?(75-n)/17.0*1.5:1.5,y=1.35+Math.sin(a*1.7+i)*.65;d.teleport(p.getLocation().add(Math.cos(a)*r,y,Math.sin(a)*r));}
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1.2,0),5,.5,.7,.5,.03);
        }},0,1);
    }
    private void stopFx(UUID id){FxState state=fx.remove(id);if(state==null)return;if(state.task!=null)state.task.cancel();state.displays.forEach(Entity::remove);}

    private void setupBoard(Player p){Scoreboard b=Bukkit.getScoreboardManager().getNewScoreboard();Objective o=b.registerNewObjective("speedsmp","dummy","§b⚡ §lSPEED SMP");o.setDisplaySlot(DisplaySlot.SIDEBAR);o.numberFormat(NumberFormat.blank());p.setScoreboard(b);refresh(p);}
    private void refresh(Player p){Scoreboard b=p.getScoreboard();Objective o=b.getObjective("speedsmp");if(o==null)return;for(String s:new HashSet<>(b.getEntries()))b.resetScores(s);Combo c=combos.get(p.getUniqueId());if(c!=null&&c.expires()<System.currentTimeMillis()){combos.remove(p.getUniqueId());c=null;}o.getScore("§fSpeed: "+format(getSpeed(p))).setScore(3);o.getScore("§fCombo: §b"+(c==null?"-":c.hits())).setScore(2);o.getScore("§fCible: §7"+(c==null?"-":trim(c.name(),16))).setScore(1);}
    private void refreshAll(){for(Player p:Bukkit.getOnlinePlayers())refresh(p);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equalsIgnoreCase("speeditem"))return giveCmd(sender,args);
        if(command.getName().equalsIgnoreCase("withdraw"))return withdraw(sender,args);
        if(args.length==0){if(sender instanceof Player p)sender.sendMessage("§bSpeed: "+format(getSpeed(p)));return true;}
        if(!sender.hasPermission("speedsmp.admin")){sender.sendMessage("§cPermission refusee.");return true;}
        if(args[0].equalsIgnoreCase("give"))return giveCmd(sender,Arrays.copyOfRange(args,1,args.length));
        if((args[0].equalsIgnoreCase("set")||args[0].equalsIgnoreCase("add"))&&args.length>=3){Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cJoueur introuvable.");return true;}try{int v=Integer.parseInt(args[2]);setSpeed(p,args[0].equalsIgnoreCase("add")?getSpeed(p)+v:v);refreshAll();sender.sendMessage("§aOK: "+p.getName()+" = "+format(getSpeed(p)));}catch(NumberFormatException ex){sender.sendMessage("§cNombre invalide.");}return true;}
        return true;
    }
    private boolean withdraw(CommandSender sender,String[] args){
        if(!(sender instanceof Player p)){sender.sendMessage("§cCommande en jeu uniquement.");return true;}int amount=1;
        if(args.length>0)try{amount=Integer.parseInt(args[0]);}catch(NumberFormatException ex){p.sendMessage("§cQuantite invalide.");return true;}
        int available=getSpeed(p)-MIN;if(amount<1||amount>available){p.sendMessage("§cTu peux retirer entre 1 et "+available+" fragment(s).");return true;}
        setSpeed(p,getSpeed(p)-amount);giveFragments(p,amount);refreshAll();p.sendMessage("§aTu as retire §f"+amount+" §afragment(s). Nouvelle Speed: "+format(getSpeed(p)));return true;
    }
    private boolean giveCmd(CommandSender sender,String[] args){
        if(!sender.hasPermission("speedsmp.admin")){sender.sendMessage("§cPermission refusee.");return true;}Player p=sender instanceof Player pl?pl:null;int amount=1;
        if(args.length>=1){try{amount=Integer.parseInt(args[0]);}catch(NumberFormatException ex){p=Bukkit.getPlayerExact(args[0]);}}
        if(args.length>=2)try{amount=Integer.parseInt(args[1]);}catch(NumberFormatException ignored){}
        if(p==null){sender.sendMessage("§cJoueur introuvable.");return true;}amount=Math.max(1,Math.min(64,amount));giveFragments(p,amount);sender.sendMessage("§a"+amount+" fragment(s) donne(s) a "+p.getName()+".");return true;
    }
    private void giveFragments(Player p,int amount){for(int n=0;n<amount;n++){ItemStack item=createFragment();HashMap<Integer,ItemStack> left=p.getInventory().addItem(item);for(ItemStack remaining:left.values())spawnFragment(p.getLocation(),remaining);}}

    private int getSpeed(Player p){return speed.getOrDefault(p.getUniqueId(),0);}private int setSpeed(Player p,int v){v=Math.max(MIN,Math.min(MAX,v));speed.put(p.getUniqueId(),v);save();return v;}
    private String format(int v){return (v>0?"§a+":v<0?"§c":"§7")+v;}private String trim(String s,int n){return s.length()<=n?s:s.substring(0,n-1)+"…";}
    private void load(){speed.clear();if(!dataFile.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(dataFile);for(String k:y.getKeys(false))try{speed.put(UUID.fromString(k),y.getInt(k));}catch(Exception ignored){}}
    private void save(){YamlConfiguration y=new YamlConfiguration();speed.forEach((u,v)->y.set(u.toString(),v));try{if(!getDataFolder().exists())getDataFolder().mkdirs();y.save(dataFile);}catch(IOException e){getLogger().severe(e.getMessage());}}
    private enum Weapon{SWORD,AXE}
    private record Combo(UUID target,String name,Weapon weapon,int hits,long expires){}
    private static final class FxState{final List<ItemDisplay> displays=new ArrayList<>();BukkitTask task;}
}
