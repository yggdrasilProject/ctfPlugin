package ru.linachan.ctf;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import ru.linachan.ctf.common.Utils;
import ru.linachan.yggdrasil.YggdrasilCore;
import ru.linachan.yggdrasil.plugin.YggdrasilPluginManager;
import ru.linachan.yggdrasil.service.YggdrasilService;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CTFFlagSender implements YggdrasilService {

    private CTFPlugin ctfPlugin;
    private static final String[] THEMIS_ERRORS = new String[] {
        "Submitted flag has been accepted",
        "Generic error",
        "The attacker does not appear to be a team",
        "Contest has not been started yet",
        "Contest has been paused",
        "Contest has been completed",
        "Submitted data has invalid format",
        "Attack attempts limit exceeded",
        "Submitted flag has expired",
        "Submitted flag belongs to the attacking team and therefore won't be accepted",
        "Submitted flag has been accepted already",
        "Submitted flag has not been found",
        "The attacking team service is not up and therefore flags from the same services of other teams won't be accepted"
    };

    private boolean isRunning = true;

    @Override
    public void onInit() {
        ctfPlugin = YggdrasilCore.INSTANCE.getManager(YggdrasilPluginManager.class).get(CTFPlugin.class);
    }

    @Override
    public void onShutdown() {
        isRunning = false;
    }

    @Override
    public void run() {
        MongoCollection<Document> flags = ctfPlugin.getDB().getCollection("flags");

        while (isRunning) {
            try {
                Document flag = flags.find(new Document("state", 0)).sort(new Document("priority", -1).append("timestamp", 1)).first();
                if (flag != null) {
                    try {
                        JSONArray result = sendFlag(flag.getString("flag"));
                        int responseCode = (int) (long) result.get(0);

                        switch (responseCode) {
                            case 0:
                                logger.info(
                                    "Flag[{}:{}] sent!",
                                    (flag.getInteger("priority") > 0) ? "HI" : "LO", flag.getString("flag")
                                );
                                flags.updateOne(flag, new Document("$set", new Document("state", 1)));
                                break;
                            case 1:
                            case 3:
                            case 4:
                            case 7:
                            case 12:
                                logger.info(
                                    "Flag[{}:{}] was not accepted: {}",
                                    (flag.getInteger("priority") > 0) ? "HI" : "LO", flag.getString("flag"),
                                    THEMIS_ERRORS[responseCode]
                                );
                                Utils.sleep(5000);
                                break;
                            case 6:
                            case 8:
                            case 9:
                            case 10:
                            case 11:
                                logger.info(
                                    "Flag[{}:{}] is invalid: {}",
                                    (flag.getInteger("priority") > 0) ? "HI" : "LO", flag.getString("flag"),
                                    THEMIS_ERRORS[responseCode]
                                );
                                flags.updateOne(flag, new Document("$set", new Document("state", 2)));
                                break;
                            case 5:
                                logger.error("Contest is finished, so no flags accepted");
                                Utils.sleep(15000);
                                break;
                            case 2:
                                logger.error("Something wrong with network configuration!");
                                Utils.sleep(15000);
                                break;
                        }
                    } catch (IOException e) {
                        logger.error("unable to process flag: {}", e.getMessage());
                    }
                } else {
                    Thread.sleep(300);
                }
            } catch (InterruptedException ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private JSONArray sendFlag(String flag) throws IOException {
        URL checkerURL = new URL(String.format(
            "http://%s/api/submit",
            YggdrasilCore.INSTANCE.getConfig().getString("ctf.themis.host", "localhost")
        ));

        HttpURLConnection httpConnection = (HttpURLConnection) checkerURL.openConnection();

        httpConnection.setRequestMethod("POST");
        httpConnection.addRequestProperty("Content=Type", "application/json");
        httpConnection.setDoOutput(true);

        JSONArray flagJSON = new JSONArray();
        flagJSON.add(flag);

        DataOutputStream wr = new DataOutputStream(httpConnection.getOutputStream());

        wr.writeBytes(flagJSON.toJSONString());
        wr.flush();
        wr.close();

        JSONParser parser = new JSONParser();
        JSONArray response = null;

        try {
            response = (JSONArray) parser.parse(new InputStreamReader(httpConnection.getInputStream()));
        } catch (ParseException e) {
            logger.error("Unable to parse Themis response: {}", e.getMessage());
        }

        return response;
    }
}
