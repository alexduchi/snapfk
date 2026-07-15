package fr.neo.speedsmp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.*;

final class TrackerService implements Listener,CommandExecutor{
    private static final int PAGE=45;
    private final SpeedSmpPlugin plugin;
    private final Map<UUID,Tracked> tracked=new LinkedHashMap<>();
    private final Map<UUID,Integer> pages=new HashMap<>();
    private final NamespacedKey guiKey;
    private final File file;
    private BukkitTask task;

    TrackerService(SpeedSmpPlugin plugin){this.plugin=plugin;guiKey=new NamespacedKey(plugin,"gui_fragment");file=new File(plugin.getDataFolder(),"fragments.yml");load();}
    void start(){scanLoaded();task=Bukkit.getScheduler().runTaskTimer(plugin,()->{updateLoaded();save();refreshOpen();},20,20);}
    void stop(){if(task!=null)task.cancel();save();}

    void register(Item item){UUID id=plugin.fragmentId(item.getItemStack());if(id==null)return;Location l=item.getLocation();tracked.put(id,new Tracked(id,item.getUniqueId(),l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),System.currentTimeMillis()));}
    private void remove(ItemStack stack){UUID id=plugin.fragmentId(stack);if(id!=null){tracked.remove(id);save();refreshOpen();}}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(plugin.isFragment(e.getItem().getItemStack())&&e.getRemaining()==0)remove(e.getItem().getItemStack());}
    @EventHandler public void chunk(ChunkLoadEvent e){Bukkit.getScheduler().runTask(plugin,()->{for(Entity entity:e.getChunk().getEntities())if(entity instanceof Item item&&plugin.isFragment(item.getItemStack())){plugin.protect(item);register(item);}refreshOpen();});}
    @EventHandler public void click(InventoryClickEvent e){
        if(!isTitle(e.getView().getTitle()))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;int slot=e.getRawSlot();
        if(slot==45){pages.put(p.getUniqueId(),Math.max(0,pages.getOrDefault(p.getUniqueId(),0)-1));open(p);return;}if(slot==53){pages.put(p.getUniqueId(),pages.getOrDefault(p.getUniqueId(),0)+1);open(p);return;}
        ItemStack item=e.getCurrentItem();if(item==null||!item.hasItemMeta())return;String raw=item.getItemMeta().getPersistentDataContainer().get(guiKey,PersistentDataType.STRING);if(raw==null)return;
        try{Tracked t=tracked.get(UUID.fromString(raw));if(t==null)return;World w=Bukkit.getWorld(t.world);if(w==null){p.sendMessage("§cMonde introuvable.");return;}p.teleport(new Location(w,t.x,t.y+1,t.z));p.sendMessage("§aTeleportation vers §f"+shortId(t.id)+"§a.");}catch(Exception ignored){}
    }

    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){if(!(s instanceof Player p)){s.sendMessage("§cCommande en jeu uniquement.");return true;}if(!p.hasPermission("speedsmp.seespeed")){p.sendMessage("§cPermission refusee.");return true;}int page=0;if(a.length>0)try{page=Math.max(0,Integer.parseInt(a[0])-1);}catch(Exception ignored){}pages.put(p.getUniqueId(),page);open(p);return true;}

    private void open(Player p){updateLoaded();List<Tracked> list=sorted();int max=Math.max(0,(list.size()-1)/PAGE),page=Math.max(0,Math.min(pages.getOrDefault(p.getUniqueId(),0),max));pages.put(p.getUniqueId(),page);Inventory inv=Bukkit.createInventory(null,54,title(page,max,list.size()));render(inv,list,page,max);p.openInventory(inv);}
    private void render(Inventory inv,List<Tracked> list,int page,int max){inv.clear();int start=page*PAGE;for(int slot=0;slot<PAGE&&start+slot<list.size();slot++)inv.setItem(slot,icon(list.get(start+slot)));if(page>0)inv.setItem(45,nav(Material.ARROW,"§bPage precedente"));inv.setItem(49,nav(Material.COMPASS,"§f"+list.size()+" fragment(s) au sol"));if(page<max)inv.setItem(53,nav(Material.ARROW,"§bPage suivante"));}
    private void refreshOpen(){List<Tracked> list=sorted();int max=Math.max(0,(list.size()-1)/PAGE);for(Player p:Bukkit.getOnlinePlayers())if(isTitle(p.getOpenInventory().getTitle())){int page=Math.max(0,Math.min(pages.getOrDefault(p.getUniqueId(),0),max));pages.put(p.getUniqueId(),page);render(p.getOpenInventory().getTopInventory(),list,page,max);}}

    private ItemStack icon(Tracked t){ItemStack i=new ItemStack(Material.POTION);PotionMeta m=(PotionMeta)i.getItemMeta();boolean loaded=loaded(t);m.setColor(loaded?Color.fromRGB(66,220,255):Color.fromRGB(90,90,120));m.setDisplayName("§b"+t.world+" §8• §f"+round(t.x)+" "+round(t.y)+" "+round(t.z));m.setLore(List.of(loaded?"§a● Zone chargee : coordonnees en direct":"§7● Chunk decharge : derniere position connue","§7ID : §f"+shortId(t.id),"§7Mise a jour : §f"+age(t.updated),"","§eClic pour te teleporter"));m.getPersistentDataContainer().set(guiKey,PersistentDataType.STRING,t.id.toString());m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);i.setItemMeta(m);return i;}
    private ItemStack nav(Material type,String name){ItemStack i=new ItemStack(type);ItemMeta m=i.getItemMeta();m.setDisplayName(name);i.setItemMeta(m);return i;}

    private void scanLoaded(){for(World w:Bukkit.getWorlds())for(Item i:w.getEntitiesByClass(Item.class))if(plugin.isFragment(i.getItemStack())){plugin.protect(i);register(i);}}
    private void updateLoaded(){
        Map<UUID,Item> loaded=new HashMap<>();for(World w:Bukkit.getWorlds())for(Item i:w.getEntitiesByClass(Item.class))if(plugin.isFragment(i.getItemStack())){UUID id=plugin.fragmentId(i.getItemStack());if(id!=null)loaded.put(id,i);}for(Item i:loaded.values())register(i);
        Iterator<Map.Entry<UUID,Tracked>> it=tracked.entrySet().iterator();while(it.hasNext()){Map.Entry<UUID,Tracked> e=it.next();Tracked t=e.getValue();World w=Bukkit.getWorld(t.world);if(w!=null&&w.isChunkLoaded(chunk(t.x),chunk(t.z))&&!loaded.containsKey(e.getKey()))it.remove();}
    }
    private boolean loaded(Tracked t){World w=Bukkit.getWorld(t.world);return w!=null&&w.isChunkLoaded(chunk(t.x),chunk(t.z));}
    private int chunk(double n){return((int)Math.floor(n))>>4;}
    private List<Tracked> sorted(){List<Tracked> l=new ArrayList<>(tracked.values());l.sort(Comparator.comparing((Tracked t)->t.world).thenComparingDouble(t->t.x).thenComparingDouble(t->t.z));return l;}
    private String title(int p,int max,int total){return"§8Speed au sol §7["+(p+1)+"/"+(max+1)+"] §8("+total+")";}
    private boolean isTitle(String s){String clean=ChatColor.stripColor(s);return clean!=null&&clean.startsWith("Speed au sol");}
    private int round(double n){return(int)Math.round(n);}private String shortId(UUID id){return id.toString().substring(0,8);}private String age(long time){long s=Math.max(0,(System.currentTimeMillis()-time)/1000);return s<60?s+" s":s<3600?s/60+" min":s/3600+" h";}

    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=y.getConfigurationSection("fragments");if(root==null)return;for(String key:root.getKeys(false))try{UUID id=UUID.fromString(key);String p="fragments."+key+".";tracked.put(id,new Tracked(id,UUID.fromString(y.getString(p+"entity",UUID.randomUUID().toString())),y.getString(p+"world","world"),y.getDouble(p+"x"),y.getDouble(p+"y"),y.getDouble(p+"z"),y.getLong(p+"updated",System.currentTimeMillis())));}catch(Exception ignored){}}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(Tracked t:tracked.values()){String p="fragments."+t.id+".";y.set(p+"entity",t.entity.toString());y.set(p+"world",t.world);y.set(p+"x",t.x);y.set(p+"y",t.y);y.set(p+"z",t.z);y.set(p+"updated",t.updated);}try{if(!plugin.getDataFolder().exists())plugin.getDataFolder().mkdirs();y.save(file);}catch(IOException e){plugin.getLogger().severe("Erreur fragments.yml: "+e.getMessage());}}
    private record Tracked(UUID id,UUID entity,String world,double x,double y,double z,long updated){}
}
