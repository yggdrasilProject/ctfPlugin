package ru.linachan.ctf;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import ru.linachan.ctf.common.ExploitExecutor;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.plugin.YggdrasilPlugin;
import ru.linachan.yggdrasil.plugin.helpers.AutoStart;
import ru.linachan.yggdrasil.plugin.helpers.Plugin;
import ru.linachan.yggdrasil.scheduler.YggdrasilTask;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@AutoStart
@Plugin(name = "ctf", description = "Provides useful utils for CTF.")
public class CTFPlugin implements YggdrasilPlugin {

    private CTFDataBase db;
    private Map<String, YggdrasilTask> executors = new ConcurrentHashMap<>();

    @Override
    public void onInit() {
        YggdrasilCore.INSTANCE.createQueue(String.class, "ctfFlags");

        db = new CTFDataBase(
            YggdrasilCore.INSTANCE.getConfig().getString("ctf.db.uri", "mongodb://127.0.0.1:27017/"),
            YggdrasilCore.INSTANCE.getConfig().getString("ctf.db.name", "ctf")
        );

        db.getCollection("flags").createIndex(new Document("flag", 1), new IndexOptions().unique(true));
    }

    public CTFDataBase getDB() {
        return db;
    }

    public void scheduleExecution(String exploitName, ExploitExecutor executor, int initDelay, int execPeriod) {
        if (executors.containsKey(exploitName)) {
            throw new IllegalStateException(String.format("Exploit %s already registered", exploitName));
        }

        YggdrasilTask executionTask = new YggdrasilTask(exploitName, executor, initDelay, execPeriod, TimeUnit.SECONDS);
        executors.put(exploitName, executionTask);
        YggdrasilCore.INSTANCE.getScheduler().scheduleTask(executionTask);
    }

    public void cancelExecution(String exploitName) {
        if (executors.containsKey(exploitName)) {
            YggdrasilTask executionTask = YggdrasilCore.INSTANCE.getScheduler().getTask(exploitName);
            executionTask.cancelTask();
        }
    }

    public Collection<YggdrasilTask> listExecutions() {
        return executors.values();
    }

    @Override
    public void onShutdown() {

    }
}
