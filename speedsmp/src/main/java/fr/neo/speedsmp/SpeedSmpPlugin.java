package fr.neo.speedsmp;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
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
    private static final int MIN=-3, MAX=3;
    private final Map<UUID,Integer> speed=new HashMap<>();
    private final Map<UUID,Combo> combos=new HashMap<>();
    private final Map<UUID,List<ItemDisplay>> fx=new HashMap<>();
    private NamespacedKey fragmentKey;
    private File dataFile;

    @Override public void onEnable(){
        fragmentKey=new NamespacedKey(this,"fragment"); dataFile=new File(getDataFolder(),"players.yml"); load();
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("speed")).setExecutor(this); Objects.requireNonNull(getCommand("speeditem")).setExecutor(this);
        for(Player p:Bukkit.getOnlinePlayers()) setupBoard(p);
        Bukkit.getScheduler().runTaskTimer(this,()->{for(Player p:Bukkit.getOnlinePlayers()) refresh(p);},10,10);
        getLogger().info("SpeedSMP 1.0.3 enabled.");
    }
    @Override public void onDisable(){ save(); fx.values().forEach(l->l.forEach(Entity::remove)); }

    @EventHandler public void join(PlayerJoinEvent e){setupBoard(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){combos.remove(e.getPlayer().getUniqueId()); stopFx(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){
        Player p=e.getEntity(); setSpeed(p,getSpeed(p)-1); combos.remove(p.getUniqueId());
        ItemStack fragment=createFragment(); Item item=p.getWorld().dropItemNaturally(p.getLocation(),fragment);
        item.setUnlimitedLifetime(true); item.setInvulnerable(true); item.setPersistent(true); item.setCanMobPickup(false); item.setWillAge(false);
        getLogger().info("Speed fragment dropped at "+p.getLocation());
    }
    @EventHandler public void burn(EntityCombustEvent e){if(e.getEntity() instanceof Item i && isFragment(i.getItemStack())) e.setCancelled(true);}
    @EventHandler public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Item i && isFragment(i.getItemStack())) e.setCancelled(true);}
    @EventHandler public void despawn(ItemDespawnEvent e){if(isFragment(e.getEntity().getItemStack())) e.setCancelled(true);}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
        Material m=p.getInventory().getItemInMainHand().getType(); Weapon w=m.name().endsWith("_SWORD")?Weapon.SWORD:m.name().endsWith("_AXE")?Weapon.AXE:null;
        if(w==null){combos.remove(p.getUniqueId());return;}
        Combo old=combos.get(p.getUniqueId()); long now=System.currentTimeMillis();
        int hits=(old!=null&&old.target.equals(target.getUniqueId())&&old.weapon==w&&old.expires>now)?old.hits+1:1;
        Combo c=new Combo(target.getUniqueId(),target.getName(),w,hits,now+3500); combos.put(p.getUniqueId(),c);
        if(w==Weapon.SWORD){
            int amp=Math.min(2,hits-1); p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,55,amp,true,false,true));
            if(getSpeed(p)==3&&hits>=4){e.setDamage(e.getDamage()*1.5); target.getWorld().spawnParticle(Particle.CRIT,target.getLocation().add(0,target.getHeight()/2,0),12,.3,.4,.3,.1);}
        }else{
            if(isCritical(p)) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,35,0,true,true,true));
            if(hits>=5){freeze(target,getSpeed(p)==3?80:40); combos.remove(p.getUniqueId());}
        }
        refresh(p);
    }
    private boolean isCritical(Player p){return p.getFallDistance()>0&&!p.isOnGround()&&!p.isInWater()&&!p.isClimbing()&&!p.hasPotionEffect(PotionEffectType.BLINDNESS);}
    private void freeze(LivingEntity e,int ticks){
        boolean ai=e instanceof Mob m&&m.hasAI(); if(e instanceof Mob m)m.setAI(false); Location lock=e.getLocation().clone();
        BukkitTask[] task=new BukkitTask[1]; task[0]=Bukkit.getScheduler().runTaskTimer(this,new Runnable(){int t=0;public void run(){if(!e.isValid()||t++>=ticks){if(e instanceof Mob m)m.setAI(ai);task[0].cancel();return;} e.teleport(lock);e.setVelocity(new Vector());e.getWorld().spawnParticle(Particle.SNOWFLAKE,e.getLocation().add(0,e.getHeight()/2,0),3,.3,.5,.3,.01);}},0,1);
    }

    @EventHandler public void use(PlayerInteractEvent e){
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;
        ItemStack item=e.getItem(); if(!isFragment(item))return; e.setCancelled(true); Player p=e.getPlayer();
        if(getSpeed(p)>=MAX){p.sendMessage("§cTa Speed est deja au maximum.");return;}
        item.setAmount(item.getAmount()-1); int before=getSpeed(p),after=setSpeed(p,before+1); playFx(p,before,after); refreshAll();
    }

    private ItemStack createFragment(){
        ItemStack it=new ItemStack(Material.POTION); PotionMeta m=(PotionMeta)it.getItemMeta();
        m.setDisplayName("§b§l✦ FRAGMENT DE VELOCITE ✦"); m.setColor(Color.fromRGB(66,220,255));
        m.setLore(List.of("§7Une essence arrachee a la vitesse", "§7d'un combattant tombe au combat.","","§fClic droit pour absorber son energie.","§b➜ §fAugmente ta Speed de §a+1","§b➜ §fNiveau maximal : §d+3","§b➜ §fObjet eternel et indestructible","","§8« La vitesse change simplement de maitre. »"));
        m.getPersistentDataContainer().set(fragmentKey,PersistentDataType.BYTE,(byte)1); m.addEnchant(Enchantment.UNBREAKING,1,true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS,ItemFlag.HIDE_ADDITIONAL_TOOLTIP); it.setItemMeta(m); return it;
    }
    private boolean isFragment(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(fragmentKey,PersistentDataType.BYTE);}

    private void playFx(Player p,int before,int after){
        stopFx(p); p.sendTitle("","§b✦ VELOCITE ABSORBEE ✦ §7("+before+" → §f"+after+"§7)",2,18,5);
        p.getWorld().strikeLightningEffect(p.getLocation()); p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.75f,1.45f); p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,.55f,1.35f);
        List<ItemDisplay> list=new ArrayList<>(); Material[] mats={Material.POTION,Material.SUGAR,Material.FEATHER,Material.WIND_CHARGE,Material.AMETHYST_SHARD};
        for(int i=0;i<12;i++){ItemDisplay d=p.getWorld().spawn(p.getLocation().add(0,1.1,0),ItemDisplay.class);d.setItemStack(new ItemStack(mats[i%mats.length]));d.setPersistent(false);d.setInvulnerable(true);d.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),new Vector3f(.8f),new AxisAngle4f()));list.add(d);}fx.put(p.getUniqueId(),list);
        BukkitTask[] task=new BukkitTask[1];task[0]=Bukkit.getScheduler().runTaskTimer(this,new Runnable(){int t=0;public void run(){if(!p.isOnline()||t++>75){stopFx(p);task[0].cancel();return;}double phase=t*.16;for(int i=0;i<list.size();i++){ItemDisplay d=list.get(i);if(!d.isValid())continue;double a=phase+i*(Math.PI*2/list.size()),r=t<12?t/12.0*1.5:t>58?(75-t)/17.0*1.5:1.5;double y=1.35+Math.sin(a*1.7+i)*.65;d.teleport(p.getLocation().add(Math.cos(a)*r,y,Math.sin(a)*r));}p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1.2,0),5,.5,.7,.5,.03);}},0,1);
    }
    private void stopFx(Player p){List<ItemDisplay> l=fx.remove(p.getUniqueId());if(l!=null)l.forEach(Entity::remove);}

    private void setupBoard(Player p){Scoreboard b=Bukkit.getScoreboardManager().getNewScoreboard();Objective o=b.registerNewObjective("speedsmp","dummy","§b⚡ §lSPEED SMP");o.setDisplaySlot(DisplaySlot.SIDEBAR);try{o.numberFormat(NumberFormat.blank());}catch(Throwable ignored){}p.setScoreboard(b);refresh(p);}
    private void refresh(Player p){Scoreboard b=p.getScoreboard();Objective o=b.getObjective("speedsmp");if(o==null)return;for(String s:b.getEntries())b.resetScores(s);Combo c=combos.get(p.getUniqueId());if(c!=null&&c.expires<System.currentTimeMillis()){combos.remove(p.getUniqueId());c=null;}o.getScore("§fSpeed: "+format(getSpeed(p))).setScore(3);o.getScore("§fCombo: §b"+(c==null?"-":c.hits)).setScore(2);o.getScore("§fCible: §7"+(c==null?"-":trim(c.name,16))).setScore(1);}
    private void refreshAll(){for(Player p:Bukkit.getOnlinePlayers())refresh(p);}

    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(c.getName().equalsIgnoreCase("speeditem"))return giveCmd(s,a);
        if(a.length==0){if(s instanceof Player p)s.sendMessage("§bSpeed: "+format(getSpeed(p)));return true;}
        if(!s.hasPermission("speedsmp.admin")){s.sendMessage("§cPermission refusee.");return true;}
        if(a[0].equalsIgnoreCase("give")){String[] rest=Arrays.copyOfRange(a,1,a.length);return giveCmd(s,rest);}
        if((a[0].equalsIgnoreCase("set")||a[0].equalsIgnoreCase("add"))&&a.length>=3){Player p=Bukkit.getPlayerExact(a[1]);if(p==null){s.sendMessage("§cJoueur introuvable.");return true;}try{int v=Integer.parseInt(a[2]);setSpeed(p,a[0].equalsIgnoreCase("add")?getSpeed(p)+v:v);refreshAll();s.sendMessage("§aOK: "+p.getName()+" = "+format(getSpeed(p)));}catch(NumberFormatException ex){s.sendMessage("§cNombre invalide.");}return true;}return true;
    }
    private boolean giveCmd(CommandSender s,String[] a){
        if(!s.hasPermission("speedsmp.admin")){s.sendMessage("§cPermission refusee.");return true;}Player p=s instanceof Player pl?pl:null;int amount=1;
        if(a.length>=1){try{amount=Integer.parseInt(a[0]);}catch(NumberFormatException ex){p=Bukkit.getPlayerExact(a[0]);}}
        if(a.length>=2)try{amount=Integer.parseInt(a[1]);}catch(NumberFormatException ignored){}
        if(p==null){s.sendMessage("§cJoueur introuvable.");return true;}amount=Math.max(1,Math.min(64,amount));ItemStack it=createFragment();it.setAmount(amount);Map<Integer,ItemStack> left=p.getInventory().addItem(it);for(ItemStack remaining:left.values())p.getWorld().dropItemNaturally(p.getLocation(),remaining);s.sendMessage("§a"+amount+" fragment(s) donne(s) a "+p.getName()+".");return true;
    }

    private int getSpeed(Player p){return speed.getOrDefault(p.getUniqueId(),0);}private int setSpeed(Player p,int v){v=Math.max(MIN,Math.min(MAX,v));speed.put(p.getUniqueId(),v);save();return v;}
    private String format(int v){return (v>0?"§a+":"§c")+v;}private String trim(String s,int n){return s.length()<=n?s:s.substring(0,n-1)+"…";}
    private void load(){speed.clear();if(!dataFile.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(dataFile);for(String k:y.getKeys(false))try{speed.put(UUID.fromString(k),y.getInt(k));}catch(Exception ignored){}}
    private void save(){YamlConfiguration y=new YamlConfiguration();speed.forEach((u,v)->y.set(u.toString(),v));try{if(!getDataFolder().exists())getDataFolder().mkdirs();y.save(dataFile);}catch(IOException e){getLogger().severe(e.getMessage());}}
    private enum Weapon{SWORD,AXE}private record Combo(UUID target,String name,Weapon weapon,int hits,long expires){}
}
