package net.purelic.proxy.commons;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.myjeeva.digitalocean.DigitalOcean;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.rudderstack.sdk.java.RudderAnalytics;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ProxyCommons extends Plugin {

    private static Configuration config;
    private static Map<UUID, Map<String, Object>> playerCache;
    private static Map<String, Map<String, Object>> serverCache;
    private static Map<String, DocumentReference> documentCache;
    private static Map<DocumentReference, ListenerRegistration> listenerCache;
    private static Map<String, Object> generalCache;
    private static Firestore firestore;
    private static DigitalOcean digitalOcean;
    private static RudderAnalytics analytics;
    private static JDA discordBot;

    @Override
    public void onEnable() {
        config = this.getConfig();
        playerCache = new HashMap<>();
        serverCache = new HashMap<>();
        documentCache = new HashMap<>();
        listenerCache = new HashMap<>();
        generalCache = new HashMap<>();
        this.connectDatabase();
        this.cleanDatabase();
        this.connectDigitalOcean();
        this.connectAnalytics();
        this.connectDiscordBot();
    }

    @Override
    public void onDisable() {
        this.cleanDatabase();
    }

    public static Map<UUID, Map<String, Object>> getPlayerCache() {
        return playerCache;
    }

    public static Map<String, Map<String, Object>> getServerCache() {
        return serverCache;
    }

    public static Map<String, DocumentReference> getDocumentCache() {
        return documentCache;
    }

    public static Map<DocumentReference, ListenerRegistration> getListenerCache() {
        return listenerCache;
    }

    public static Map<String, Object> getGeneralCache() {
        return generalCache;
    }

    public static Firestore getFirestore() {
        return firestore;
    }

    public static DigitalOcean getDigitalOcean() {
        return digitalOcean;
    }

    public static RudderAnalytics getAnalytics() {
        return analytics;
    }

    public static JDA getDiscordBot() {
        return discordBot;
    }

    private Configuration getConfig() {
        File config = new File(this.getDataFolder(), "config.yml");

        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(config);
        } catch (IOException e) {
            this.fatalError("Error getting config file!", e);
            return null;
        }
    }

    private void connectDatabase() {
        try {
            // Path to database auth file
            String authPath = this.getDataFolder() + "/purelic-firebase-adminsdk.json";

            // Connect to Firebase Firestore
            InputStream serviceAccount = new FileInputStream(authPath);
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp.initializeApp(options);

            // Save database reference
            firestore = FirestoreClient.getFirestore();

            this.logInfo("Connected to Firebase!");
        } catch (IOException e) {
            this.fatalError("Error connecting to Firebase!", e);
        }
    }

    private void cleanDatabase() {
        this.logInfo("Cleaning up server documents...");
        this.deleteCollection(firestore.collection("servers"));
        this.deleteCollection(firestore.collection("server_ips"));
    }

    private void deleteCollection(CollectionReference collection) {
        try {
            int deleted = 0;
            ApiFuture<QuerySnapshot> future = collection.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                if (document.getData().containsKey("droplet_id")) {
                    Long dropletId = document.getLong("droplet_id");

                    if (dropletId != null) {
                        this.logInfo("Deleting droplet (" + dropletId + ")");
                        digitalOcean.deleteDroplet(dropletId.intValue());
                    }
                }

                document.getReference().delete();
                deleted++;
            }

            this.logInfo("Deleted " + deleted + " document(s) from collection " + collection.getPath());
        } catch (Exception e) {
            this.log(Level.WARNING, "Error deleting collection: " + e.getMessage());
        }
    }

    private void connectDigitalOcean() {
        String auth = config.getString("digital_ocean_auth");
        digitalOcean = new DigitalOceanClient(auth);

        this.logInfo("Connected to Digital Ocean!");
    }

    private void connectAnalytics() {
        analytics = RudderAnalytics.builder(
            config.getString("analytics.write_key"),
            config.getString("analytics.data_plane_url")
        ).build();

        this.logInfo("Connected to RudderStack!");
    }

    private void connectDiscordBot() {
        JDABuilder builder = JDABuilder
            .createDefault(config.getString("discord.bot_token"))
            .setChunkingFilter(ChunkingFilter.ALL)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .enableIntents(GatewayIntent.GUILD_MEMBERS);

        try {
            discordBot = builder.build();
            discordBot.awaitReady();
            this.logInfo("Connected to Discord!");
        } catch (LoginException | InterruptedException e) {
            this.log(Level.SEVERE, "Error starting up the Discord bot!");
            e.printStackTrace();
        }
    }

    private void fatalError(String message, Exception e) {
        this.getLogger().log(Level.SEVERE, message + " Shutting down proxy...");
        e.printStackTrace();
        this.getProxy().stop();
    }

    private void logInfo(String message) {
        this.log(Level.INFO, message);
    }

    private void log(Level level, String message) {
        this.getLogger().log(level, message);
    }

}
