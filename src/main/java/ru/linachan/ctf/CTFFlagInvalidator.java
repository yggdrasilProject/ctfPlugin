package ru.linachan.ctf;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.service.YggdrasilService;

public class CTFFlagInvalidator implements YggdrasilService {

    private CTFPlugin ctfPlugin;
    private boolean isRunning = true;
    private long flagTimeToLive;

    @Override
    public void onInit() {
        ctfPlugin = YggdrasilCore.INSTANCE.getManager(YggdrasilPluginManager.class).get(CTFPlugin.class);
        flagTimeToLive = YggdrasilCore.INSTANCE.getConfig().getLong("ctf.flag.ttl", 300L) * 1000L;
    }

    @Override
    public void onShutdown() {
        isRunning = false;
    }

    @Override
    public void run() {
        MongoCollection<Document> flags = ctfPlugin.getDB().getCollection("flags");

        while (isRunning) {

            long invalidated = flags.updateMany(
                new Document("timestamp", new Document("$lt", System.currentTimeMillis() - flagTimeToLive))
                    .append("state", 0),
                new Document("$set", new Document("state", 2).append("updateTime", System.currentTimeMillis()))
            ).getModifiedCount();

            if (invalidated > 0L) {
                logger.info("{} flags expired.", invalidated);
            }

            try { Thread.sleep(3000); } catch(InterruptedException ignored) {}
        }
    }
}
