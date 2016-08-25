package ru.linachan.ctf.shell;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import ru.linachan.ctf.CTFPlugin;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.common.Queue;
import ru.linachan.yggdrasil.common.console.tables.Table;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.shell.YggdrasilShellCommand;
import ru.linachan.yggdrasil.shell.helpers.CommandAction;
import ru.linachan.yggdrasil.shell.helpers.ShellCommand;

import java.io.IOException;

@ShellCommand(command = "ctf", description = "Provides access to useful CTF utils.")
public class CTFCommand extends YggdrasilShellCommand {

    private CTFPlugin ctfPlugin;
    private Queue<String> flagQueue;

    @Override
    @SuppressWarnings("unchecked")
    protected void init() throws IOException {
        ctfPlugin = YggdrasilCore.INSTANCE
            .getManager(YggdrasilPluginManager.class)
            .get(CTFPlugin.class);

        flagQueue = (Queue<String>) YggdrasilCore.INSTANCE.getQueue("ctfFlags");
    }

    @CommandAction("Put flag")
    public void put_flag() throws IOException {
        if (args.size() > 0) {
            args.forEach(flag -> flagQueue.push(flag));
        } else {
            String line;
            while ((line = console.readLine()).length() > 0) {
                flagQueue.push(line);
            }
        }
    }

    @CommandAction("Show flag queue status")
    public void queue_status() throws IOException {
        MongoCollection<Document> flags = ctfPlugin.getDB().getCollection("flags");

        if (kwargs.containsKey("f")||kwargs.containsKey("follow")) {
            console.writeLine("%10s | %10s | %10s | %10s", "Queued", "Sent", "Error", "Total");
            while (isRunning()) {
                console.writeLine(
                    "%10s | %10s | %10s | %10s",
                    String.valueOf(flags.count(new Document("state", 0))),
                    String.valueOf(flags.count(new Document("state", 1))),
                    String.valueOf(flags.count(new Document("state", 2))),
                    String.valueOf(flags.count())
                );

                try { Thread.sleep(5000); } catch(InterruptedException ignored) {}
            }
        } else {
            Table queueStatus = new Table("Queued", "Sent", "Error", "Total");
            queueStatus.addRow(
                String.valueOf(flags.count(new Document("state", 0))),
                String.valueOf(flags.count(new Document("state", 1))),
                String.valueOf(flags.count(new Document("state", 2))),
                String.valueOf(flags.count())
            );
            console.writeTable(queueStatus);
        }
    }

    @CommandAction("Clean flag database")
    public void cleanup() throws IOException {
        if (console.readYesNo("Are you sure?")) {
            if (console.readYesNo("Do you really want to delete all flags?")) {
                MongoCollection<Document> flags = ctfPlugin.getDB().getCollection("flags");

                flags.deleteMany(new Document("state", 0));
                flags.deleteMany(new Document("state", 1));
                flags.deleteMany(new Document("state", 2));
            }
        }
    }

    @Override
    protected void onInterrupt() {

    }
}
