package uk.org.whoami.authme;

import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

import me.muizers.Notifications.Notification;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import uk.org.whoami.authme.api.API;
import uk.org.whoami.authme.cache.auth.PlayerAuth;
import uk.org.whoami.authme.cache.auth.PlayerCache;
import uk.org.whoami.authme.cache.backup.FileCache;
import uk.org.whoami.authme.cache.limbo.LimboCache;
import uk.org.whoami.authme.cache.limbo.LimboPlayer;
import uk.org.whoami.authme.datasource.DataSource;
import uk.org.whoami.authme.events.AuthMeTeleportEvent;
import uk.org.whoami.authme.events.LoginEvent;
import uk.org.whoami.authme.events.RestoreInventoryEvent;
import uk.org.whoami.authme.events.SpawnTeleportEvent;
import uk.org.whoami.authme.listener.AuthMePlayerListener;
import uk.org.whoami.authme.security.PasswordSecurity;
import uk.org.whoami.authme.security.RandomString;
import uk.org.whoami.authme.settings.Messages;
import uk.org.whoami.authme.settings.PlayersLogs;
import uk.org.whoami.authme.settings.Settings;
import uk.org.whoami.authme.settings.Spawn;

public class Management {
    private Messages m = Messages.getInstance();
    private PlayersLogs pllog = PlayersLogs.getInstance();
    private Utils utils = Utils.getInstance();
    private FileCache playerCache = new FileCache();
    private DataSource database;
    public AuthMe plugin;
    public static RandomString rdm = new RandomString(Settings.captchaLength);
    public PluginManager pm;

    public Management(DataSource database, AuthMe plugin) {
        this.database = database;
        this.plugin = plugin;
        this.pm = plugin.getServer().getPluginManager();
    }

    public void performLogin(final Player player, final String password, final boolean passpartu) {
        if (passpartu) {
            // Passpartu-Login Bypasses Password-Authentication.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new AsyncronousPasspartuLogin(player));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, new AsyncronousLogin(player, password));
        }
    }
    
    public Location getSpawnLocation(World world) {
        Location spawnLoc = world.getSpawnLocation();
        if (plugin.mv != null) {
            try {
                spawnLoc = plugin.mv.getMVWorldManager().getMVWorld(world).getSpawnLocation();
            } catch (NullPointerException npe) {
            } catch (ClassCastException cce) {
            } catch (NoClassDefFoundError ncdfe) {
            }
        }
        if (plugin.essentialsSpawn != null) {
            spawnLoc = plugin.essentialsSpawn;
        }
        if (Spawn.getInstance().getLocation() != null)
            spawnLoc = Spawn.getInstance().getLocation();
        return spawnLoc;
    }

    class AsyncronousLogin implements Runnable {
        protected Player player;
        protected String name;
        protected String password;

        public AsyncronousLogin(Player player, String password) {
            this.player = player;
            this.password = password;
            name = player.getName().toLowerCase();
        }
        
        protected String getIP() {
            String ip = player.getAddress().getAddress().getHostAddress();
            if (Settings.bungee) {
                if (plugin.realIp.containsKey(name))
                    ip = plugin.realIp.get(name);
            }
            return ip;
        }
        protected boolean needsCaptcha() {
            if (Settings.useCaptcha) {
                if (!plugin.captcha.containsKey(name)) {
                    plugin.captcha.put(name, 1);
                } else {
                    int i = plugin.captcha.get(name) + 1;
                    plugin.captcha.remove(name);
                    plugin.captcha.put(name, i);
                }
                if (plugin.captcha.containsKey(name) && plugin.captcha.get(name) > Settings.maxLoginTry) {
                    player.sendMessage(m._("need_captcha"));
                    plugin.cap.put(name, rdm.nextString());
                    player.sendMessage("Type : /captcha " + plugin.cap.get(name));
                    return true;
                } else if (plugin.captcha.containsKey(name) && plugin.captcha.get(name) > Settings.maxLoginTry) {
                    try {
                        plugin.captcha.remove(name);
                        plugin.cap.remove(name);
                    } catch (NullPointerException npe) {
                    }
                }
            }
            return false;
        }
        
        /**
         * Checks the precondition for authentication (like user known) and returns the playerAuth-State
         */
        protected PlayerAuth preAuth() {
            if (PlayerCache.getInstance().isAuthenticated(name)) {
                player.sendMessage(m._("logged_in"));
                return null;
            }
            if (!database.isAuthAvailable(player.getName().toLowerCase())) {
                player.sendMessage(m._("user_unknown"));
                return null;
            }
            PlayerAuth pAuth = database.getAuth(name);
            if (pAuth == null) {
                player.sendMessage(m._("user_unknown"));
                return null;
            }
            if (!Settings.getMySQLColumnGroup.isEmpty() && pAuth.getGroupId() == Settings.getNonActivatedGroup) {
                player.sendMessage(m._("vb_nonActiv"));
                return null;
            }
            return pAuth;
        }

        @Override
        public void run() {
            PlayerAuth pAuth = preAuth();
            if (pAuth == null || needsCaptcha())
                return;
            
            String hash = pAuth.getHash();
            String email = pAuth.getEmail();
            boolean passwordVerified = true;
            try {
                passwordVerified = PasswordSecurity.comparePasswordWithHash(password, hash, name);
            } catch (NoSuchAlgorithmException ex) {
                ConsoleLogger.showError(ex.getMessage());
                player.sendMessage(m._("error"));
                return;
            }
            if (passwordVerified && player.isOnline()) {
                PlayerAuth auth = new PlayerAuth(name, hash, getIP(), new Date().getTime(), email);
                database.updateSession(auth);
                
                /*
                 * Little Work Around under Registration Group Switching for
                 * admins that add Registration thru a web Scripts.
                 */
                if (Settings.isPermissionCheckEnabled
                        && AuthMe.permission.playerInGroup(player, Settings.unRegisteredGroup)
                        && !Settings.unRegisteredGroup.isEmpty()) {
                    AuthMe.permission
                            .playerRemoveGroup(player.getWorld(), player.getName(), Settings.unRegisteredGroup);
                    AuthMe.permission.playerAddGroup(player.getWorld(), player.getName(), Settings.getRegisteredGroup);
                }
                
                pllog.addPlayer(player);
                
                // TODO: Understand what these maps do...
                if (Settings.useCaptcha) {
                    if (plugin.captcha.containsKey(name)) {
                        plugin.captcha.remove(name);
                    }
                    if (plugin.cap.containsKey(name)) {
                        plugin.cap.containsKey(name);
                    }
                }
                
                player.setNoDamageTicks(0);
                player.sendMessage(m._("login"));
                
                displayOtherAccounts(auth);
                
                if (!Settings.noConsoleSpam)
                    ConsoleLogger.info(player.getName() + " logged in!");
                
                if (plugin.notifications != null) {
                    plugin.notifications.showNotification(new Notification("[AuthMe] " + player.getName() + " logged in!"));
                }
                
                // makes player isLoggedin via API
                PlayerCache.getInstance().addPlayer(auth);
                
                // As the scheduling executes the Task most likely after the current task, we schedule it in the end
                // so that we can be sure, and have not to care if it might be processed in other order.
                ProcessSyncronousPlayerLogin syncronousPlayerLogin = new ProcessSyncronousPlayerLogin(player);
                if (syncronousPlayerLogin.getLimbo() != null) {
                    // Cancel Timings
                    player.getServer().getScheduler().cancelTask(syncronousPlayerLogin.getLimbo().getTimeoutTaskId());
                    player.getServer().getScheduler().cancelTask(syncronousPlayerLogin.getLimbo().getMessageTaskId());
                }
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, syncronousPlayerLogin);
            } else if (player.isOnline()) {
                if (!Settings.noConsoleSpam)
                    ConsoleLogger.info(player.getName() + " used the wrong password");
                if (Settings.isKickOnWrongPasswordEnabled) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (AuthMePlayerListener.gameMode != null && AuthMePlayerListener.gameMode.containsKey(name)) {
                                player.setGameMode(GameMode.getByValue(AuthMePlayerListener.gameMode.get(name)));
                            }
                            
                            player.kickPlayer(m._("wrong_pwd"));
                        }
                    });
                } else {
                    player.sendMessage(m._("wrong_pwd"));
                    return;
                }
            } else {
                ConsoleLogger.showError("Player " + name + " wasn't online during login process, aborted... ");
            }
        }
    }

    class AsyncronousPasspartuLogin extends AsyncronousLogin implements Runnable {
        public AsyncronousPasspartuLogin(Player player) {
            super(player, null);
        }

        @Override
        public void run() {
            PlayerAuth pAuth = preAuth();
            if (pAuth == null)
                return;
            
            String hash = pAuth.getHash();
            String email = pAuth.getEmail();
            
            PlayerAuth auth = new PlayerAuth(name, hash, getIP(), new Date().getTime(), email);
            database.updateSession(auth);
            
            /*
             * Little Work Around under Registration Group Switching for
             * admins that add Registration thru a web Scripts.
             */
            if (Settings.isPermissionCheckEnabled
                    && AuthMe.permission.playerInGroup(player, Settings.unRegisteredGroup)
                    && !Settings.unRegisteredGroup.isEmpty()) {
                AuthMe.permission
                        .playerRemoveGroup(player.getWorld(), player.getName(), Settings.unRegisteredGroup);
                AuthMe.permission.playerAddGroup(player.getWorld(), player.getName(), Settings.getRegisteredGroup);
            }
            
            pllog.addPlayer(player);
            
            // TODO: Understand what these maps do...
            if (Settings.useCaptcha) {
                if (plugin.captcha.containsKey(name)) {
                    plugin.captcha.remove(name);
                }
                if (plugin.cap.containsKey(name)) {
                    plugin.cap.containsKey(name);
                }
            }
            
            player.setNoDamageTicks(0);
            player.sendMessage(m._("login"));
            
            displayOtherAccounts(auth);
            
            if (!Settings.noConsoleSpam)
                ConsoleLogger.info(player.getName() + " logged in!");
            
            if (plugin.notifications != null) {
                plugin.notifications.showNotification(new Notification("[AuthMe] " + player.getName() + " logged in!"));
            }
            
            // makes player isLoggedin via API
            PlayerCache.getInstance().addPlayer(auth);
            
            // As the scheduling executes the Task most likely after the current task, we schedule it in the end
            // so that we can be sure, and have not to care if it might be processed in other order.
            ProcessSyncronousPlayerLogin syncronousPlayerLogin = new ProcessSyncronousPlayerLogin(player);
            if (syncronousPlayerLogin.getLimbo() != null) {
                // Cancel Timings
                player.getServer().getScheduler().cancelTask(syncronousPlayerLogin.getLimbo().getTimeoutTaskId());
                player.getServer().getScheduler().cancelTask(syncronousPlayerLogin.getLimbo().getMessageTaskId());
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, syncronousPlayerLogin);
        }
    }
    
    class ProcessSyncronousPlayerLogin implements Runnable {
        private LimboPlayer limbo;
        private Player player;
        private String name;
        private PlayerAuth auth;
        public ProcessSyncronousPlayerLogin(Player player) {
            this.player = player;
            this.name = player.getName().toLowerCase();
            this.limbo = LimboCache.getInstance().getLimboPlayer(name);
            this.auth = database.getAuth(name);
        }
        
        public LimboPlayer getLimbo() {
            return limbo;
        }
        
        protected void restoreOpState() {
            player.setOp(limbo.getOperator());
            if (player.getGameMode() != GameMode.CREATIVE)
                player.setAllowFlight(limbo.isFlying());
            player.setFlying(limbo.isFlying());
        }
        protected void packQuitLocation() {
            utils.packCoords(auth.getQuitLocX(), auth.getQuitLocY(), auth.getQuitLocZ(), auth.getWorld(), player);
        }
        protected void teleportBackFromSpawn() {
            AuthMeTeleportEvent tpEvent = new AuthMeTeleportEvent(player, limbo.getLoc());
            pm.callEvent(tpEvent);
            if (!tpEvent.isCancelled()) {
                Location fLoc = tpEvent.getTo();
                if (!fLoc.getChunk().isLoaded()) {
                    fLoc.getChunk().load();
                }
                player.teleport(fLoc);
            }
        }
        protected void teleportToSpawn() {
            Location spawnL = getSpawnLocation(player.getWorld());
            SpawnTeleportEvent tpEvent = new SpawnTeleportEvent(player, player.getLocation(), spawnL, true);
            pm.callEvent(tpEvent);
            if (!tpEvent.isCancelled()) {
                Location fLoc = tpEvent.getTo();
                if (!fLoc.getChunk().isLoaded()) {
                    fLoc.getChunk().load();
                }
                player.teleport(fLoc);
            }
        }
        
        protected void restoreInventory() {
            RestoreInventoryEvent event = new RestoreInventoryEvent(player, limbo.getInventory(), limbo.getArmour());
            Bukkit.getServer().getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                API.setPlayerInventory(player, limbo.getInventory(), limbo.getArmour());
            }
        }
        
        @Override
        public void run() {
            /*
             * Limbo contains the State of the Player before castration for waiting on /login
             */
            if (limbo != null) {
                // Op & Flying
                restoreOpState();
                
                // Teleport
                if (Settings.isTeleportToSpawnEnabled && !Settings.isForceSpawnLocOnJoinEnabled && Settings.getForcedWorlds.contains(player.getWorld().getName())) {
                    if (Settings.isSaveQuitLocationEnabled && auth.getQuitLocY() != 0) {
                        packQuitLocation();
                    } else {
                        teleportBackFromSpawn();
                    }
                } else if (Settings.isForceSpawnLocOnJoinEnabled && Settings.getForcedWorlds.contains(player.getWorld().getName())) {
                    teleportToSpawn();
                } else if (Settings.isSaveQuitLocationEnabled && auth.getQuitLocY() != 0) { // TODO why is it checked twice?
                    packQuitLocation();
                } else {
                    teleportBackFromSpawn();
                }

                // Inventory
                // and here comes the trick, always restore inventory before chaning gamemode, othwise all inventory-
                // plugins will store an empty inv. for the old gamemode.
                if (Settings.protectInventoryBeforeLogInEnabled && player.hasPlayedBefore()) {
                    restoreInventory();
                }
                
                // Restore GameMode
                if (!Settings.forceOnlyAfterLogin) // I have no idea what this option means at all, also isn't it related to another option? 
                    player.setGameMode(GameMode.getByValue(limbo.getGameMode()));
                else
                    player.setGameMode(GameMode.SURVIVAL);
                
                // Cleanup no longer used temporary data
                LimboCache.getInstance().deleteLimboPlayer(name);
                if (playerCache.doesCacheExist(name)) {
                    playerCache.removeCache(name);
                }
            }
            
            // The Loginevent now fires (as intended) after everything is processed, so other Plugins are able to
            // determine that AuthMe is done f*cking up gamemode and inventory
            Bukkit.getServer().getPluginManager().callEvent(new LoginEvent(player, true));
            
            player.saveData();
        }
    }

    private void displayOtherAccounts(PlayerAuth auth) {
        if (!Settings.displayOtherAccounts) {
            return;
        }
        if (auth == null) {
            return;
        }
        if (this.database.getAllAuthsByName(auth).isEmpty() || this.database.getAllAuthsByName(auth) == null) {
            return;
        }
        if (this.database.getAllAuthsByName(auth).size() == 1) {
            return;
        }
        List<String> accountList = this.database.getAllAuthsByName(auth);
        String message = "[AuthMe] ";
        int i = 0;
        for (String account : accountList) {
            i++;
            message = message + account;
            if (i != accountList.size()) {
                message = message + ", ";
            } else {
                message = message + ".";
            }

        }
        for (Player player : AuthMe.getInstance().getServer().getOnlinePlayers()) {
            if (plugin.authmePermissible(player, "authme.seeOtherAccounts")) {
                player.sendMessage("[AuthMe] The player " + auth.getNickname() + " has "
                        + String.valueOf(accountList.size()) + " accounts");
                player.sendMessage(message);
            }
        }
    }

}
