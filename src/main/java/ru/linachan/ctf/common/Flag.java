package ru.linachan.ctf.common;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.simple.JSONObject;

import java.util.Date;

public class Flag {

    private ObjectId id;
    private String flag;
    private Integer state;
    private Integer priority;
    private Long time;

    public Flag(Document flagData) {
        id = flagData.getObjectId("_id");
        flag = flagData.getString("flag");
        state = flagData.getInteger("state");
        priority = flagData.getInteger("priority");
        time = flagData.getLong("updateTime");
    }

    public String getId() {
        if (id == null)
            return "N/A";
        return id.toHexString();
    }

    public String getFlag() {
        if (flag == null)
            return "N/A";
        return flag;
    }

    public String getState() {
        if (state == null)
            return "N/A";

        switch (state) {
            case 0:
                return "Queued";
            case 1:
                return "Accepted";
            case 2:
                return "Invalid";
            default:
                return "Unknown";
        }
    }

    public String getPriority() {
        if (priority == null)
            return "N/A";

        switch (priority) {
            case 0:
                return "Low";
            case 1:
                return "Normal";
            case 2:
                return "High";
            default:
                return "Unknown";
        }
    }

    public String getTime() {
        if (time == null)
            return "N/A";

        return new Date(time).toString();
    }

    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        JSONObject data = new JSONObject();

        data.put("id", getId());
        data.put("flag", getFlag());
        data.put("state", getState());
        data.put("priority", getPriority());
        data.put("time", getTime());

        return data;
    }
}

