package ru.linachan.ctf;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.plugin.YggdrasilPlugin;
import ru.linachan.yggdrasil.plugin.helpers.AutoStart;
import ru.linachan.yggdrasil.plugin.helpers.Plugin;


@AutoStart
@Plugin(name = "ctf", description = "Provides useful utils for CTF.")
public class CTFPlugin implements YggdrasilPlugin {

    private CTFDataBase db;

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

    @Override
    public void onShutdown() {

    }
}
