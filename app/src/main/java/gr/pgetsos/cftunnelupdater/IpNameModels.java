package gr.pgetsos.cftunnelupdater;

import com.google.gson.annotations.SerializedName;

public class IpNameModels {

    public static class SetNameRequest {
        @SerializedName("name")
        String name;

        public SetNameRequest(String name) {
            this.name = name;
        }
    }

    public static class GetNameResponse {
        @SerializedName("name")
        public String name;

        @SerializedName("message") // If worker sends a 404
        public String message;
    }

    public static class WorkerMessageResponse {
        @SerializedName("message")
        public String message;
        @SerializedName("key")
        public String key;
        @SerializedName("name")
        public String name;
    }
}