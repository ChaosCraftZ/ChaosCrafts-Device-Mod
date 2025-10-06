package net.chaoscraft.chaoscrafts_device_mod.client.backend;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;

import java.util.concurrent.CompletableFuture;

public class NetworkService {
    private static NetworkService INSTANCE;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    public static NetworkService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NetworkService();
        }
        return INSTANCE;
    }

    public CompletableFuture<String> fetchUrl(String url) {
        return asyncManager.submitIOTask(() -> {
            return "Response from " + url;
        });
    }

}