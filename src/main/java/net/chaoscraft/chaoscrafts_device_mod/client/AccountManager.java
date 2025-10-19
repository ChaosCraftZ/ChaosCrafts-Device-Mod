package net.chaoscraft.chaoscrafts_device_mod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.chaoscraft.chaoscrafts_device_mod.client.app.messenger.MessengerNetworkManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Locale;

public class AccountManager {
    private static AccountManager INST;
    private Account account;
    private final File accountFile;

    private AccountManager() {
        File dir = FilesManager.getPlayerDataDir();
        this.accountFile = new File(dir, "account.json");
        load();
    }

    public static synchronized AccountManager getInstance() {
        if (INST == null) INST = new AccountManager();
        return INST;
    }

    public synchronized Account getAccount() {
        if (account == null) createDefaultAccount();
        return account;
    }

    public synchronized void ensureAccountExists() {
        if (account == null) createDefaultAccount();
        save();
    }

    private void createDefaultAccount() {
        String username = "Player";
        try {
            if (Minecraft.getInstance().player != null) username = Minecraft.getInstance().player.getGameProfile().getName();
        } catch (Exception ignored) {}
        String display = username;
        String email = username.toLowerCase(Locale.ROOT) + "@rift.com";
        account = new Account(username, display, email);
    }

    public synchronized void load() {
        if (accountFile.exists()) {
            try (Reader r = new FileReader(accountFile)) {
                Gson g = new Gson();
                Account a = g.fromJson(r, Account.class);
                if (a != null) account = a;
            } catch (Exception e) {
                System.err.println("[AccountManager] Failed to load account.json: " + e);
            }
        }
        if (account == null) createDefaultAccount();
    }

    public synchronized void save() {
        try (Writer w = new FileWriter(accountFile)) {
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            g.toJson(account, w);

            try {
                if (Minecraft.getInstance().player != null) {
                    MessengerNetworkManager manager = MessengerNetworkManager.getInstance();
                    MessengerNetworkManager.Contact myContact = manager.getContact(Minecraft.getInstance().player.getUUID());
                    if (myContact != null) {
                        myContact.description = account.description;
                    }
                }
            } catch (Exception e) {
                System.err.println("[AccountManager] Failed to update network manager: " + e);
            }
        } catch (Exception e) {
            System.err.println("[AccountManager] Failed to save account.json: " + e);
        }
    }

    public static class Account {
        public String username;
        public String displayName;
        public String email;
        public String description;

        public Account(String username, String displayName, String email) {
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.description = "";
        }
    }
}
