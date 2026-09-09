package gr.pgetsos.cftunnelupdater;
public class IpRecord {
    public String name = "";
    public Long addedAt;
    public Long expiresAt;
    public boolean deleted;
    public long updatedAt;
    public boolean pending;
    public boolean expired(long now) { return !deleted && expiresAt != null && expiresAt <= now; }
}
