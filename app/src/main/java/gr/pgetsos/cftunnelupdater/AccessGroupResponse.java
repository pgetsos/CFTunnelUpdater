package gr.pgetsos.cftunnelupdater;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IncludeItem that = (IncludeItem) o;
            return ip.ip.equals(that.ip.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ip);
        }
    }

    public static class Ip {
        @SerializedName("ip")
        public String ip;
    }
}
