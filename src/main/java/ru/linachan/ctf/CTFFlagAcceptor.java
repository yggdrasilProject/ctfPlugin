package ru.linachan.ctf;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.util.regex.Pattern;

public class CTFFlagAcceptor implements YggdrasilService {

    private Queue<String> flagQueue;
    private CTFPlugin ctfPlugin;

    private boolean isRunning = true;

    private final static Pattern FLAG_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{32}=$"
    );

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");
        ctfPlugin = YggdrasilCore.INSTANCE.getManager(YggdrasilPluginManager.class).get(CTFPlugin.class);
    }

    @Override
    public void onShutdown() {
        isRunning = false;
    }

    @Override
    public void run() {
        while (isRunning) {
            String flagRecord = flagQueue.pop();
            if (flagRecord != null) {
                String[] flagData = flagRecord.split(":");
                if (FLAG_PATTERN.matcher(flagData[0]).matches()) {
                    try { acceptFlag(flagData[0], Integer.parseInt(flagData[1])); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void acceptFlag(String flag, Integer priority) throws InterruptedException {
        MongoCollection<Document> flags = ctfPlugin.getDB().getCollection("flags");
        if (flags.find(new Document("flag", flag)).first() == null) {
            flags.insertOne(
                new Document("flag", flag)
                    .append("state", 0)
                    .append("priority", priority)
                    .append("timestamp", System.currentTimeMillis())
                    .append("updateTime", System.currentTimeMillis())
            );
        }
    }
}
