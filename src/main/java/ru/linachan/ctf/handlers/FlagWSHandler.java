package ru.linachan.ctf.handlers;

import com.mongodb.client.MongoCollection;
import io.netty.channel.ChannelHandlerContext;
import org.bson.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import ru.linachan.ctf.CTFPlugin;
import ru.linachan.ctf.common.Flag;
import ru.linachan.ctf.common.WebSocketMessageHandler;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;

import java.util.Date;

@SuppressWarnings("unchecked")
public class FlagWSHandler implements WebSocketMessageHandler {

    private Queue<String> flagQueue;
    private MongoCollection<Document> flags;

    public FlagWSHandler() {
        flags = YggdrasilCore.INSTANCE
            .getManager(YggdrasilPluginManager.class).get(CTFPlugin.class)
            .getDB().getCollection("flags");

        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");
    }

    @Override
    public String handleMessage(ChannelHandlerContext ctx, String frameText) {
        JSONObject response = new JSONObject();
        try {
            JSONObject request = (JSONObject) new JSONParser().parse(frameText);
            switch ((String) request.getOrDefault("action", null)) {
                case "get_stats":
                    response.put("status", "ok");
                    JSONObject stats = new JSONObject();
                    JSONObject queued = new JSONObject();

                    queued.put("high", flags.count(new Document("state", 0).append("priority", 2)));
                    queued.put("normal", flags.count(new Document("state", 0).append("priority", 1)));
                    queued.put("low", flags.count(new Document("state", 0).append("priority", 0)));

                    stats.put("processing", flagQueue.list().size());
                    stats.put("queued", queued);
                    stats.put("sent", flags.count(new Document("state", 1)));
                    stats.put("invalid", flags.count(new Document("state", 2)));

                    JSONArray flagList = new JSONArray();

                    for (Document flag: flags.find().sort(new Document("updateTime", -1)).limit(10)) {
                        flagList.add(new Flag(flag).toJSON());
                    }

                    response.put("flags", flagList);
                    response.put("stats", stats);
                    response.put("timestamp", new Date(System.currentTimeMillis()).toString());
                    break;
                default:
                    response.put("status", "error");
                    response.put("errorMessage", "Unknown action");
                    break;
            }

        } catch (ParseException e) {
            response.put("status", "error");
            response.put("errorMessage", String.format("Invalid request: %s", e.getMessage()));
        }

        return response.toJSONString();
    }
}

