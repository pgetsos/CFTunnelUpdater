package gr.pgetsos.cftunnelupdater;

import java.util.List;

public class AccessGroupResponse {
    public Result result;
    public static class Result {
        public List<IncludeItem> include;
    }
    public static class IncludeItem {
        public Ip ip;
    }
    public static class Ip {
        public String ip;
    }
}
