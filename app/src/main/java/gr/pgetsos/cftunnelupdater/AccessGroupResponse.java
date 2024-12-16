package gr.pgetsos.cftunnelupdater;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AccessGroupResponse {

    @SerializedName("result")
    public Result result;

    @SerializedName("success")
    public boolean success;

    @SerializedName("errors")
    public List<Object> errors;

    @SerializedName("messages")
    public List<Object> messages;

    public static class Result {
        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;

        @SerializedName("uid")
        public String uid;

        @SerializedName("include")
        public List<IncludeItem> include;

        @SerializedName("require")
        public List<Object> require;

        @SerializedName("exclude")
        public List<Object> exclude;

        @SerializedName("created_at")
        public String createdAt;

        @SerializedName("updated_at")
        public String updatedAt;
    }

    public static class IncludeItem {
        @SerializedName("ip")
        public Ip ip;
    }

    public static class Ip {
        @SerializedName("ip")
        public String ip;
    }
}