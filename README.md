# CFTunnelUpdater

CFTunnelUpdater is an Android app for managing the IP addresses allowed through a
Cloudflare Access group. Add your current IP when you change networks, name saved
addresses, and set expiry dates for temporary access to services behind your tunnel.

## Cloudflare setup

1. In Cloudflare Access, create a group with **IP ranges** as a selector.
2. Add that group to the **Include** rules of your application's Access policy.
   Use a **Bypass** policy if matching IPs should access the application without signing in.
3. Check the application's other policies to ensure they allow only the access you intend.
4. Copy your **Account ID** and **Access Group ID**.
5. Create an account-scoped API token with **Access: Organizations, Identity Providers,
   and Groups** permissions to read and edit the group.
6. Enter the IDs and token in the app's **Settings**, then tap **Save**.

An offline setup guide is also available in Settings.

## Using the app

The app opens on **Status**, which shows your last checked IP, Access-group
membership, monitoring state, sync status, pending changes, and next known expiry.
Use **Check now** to refresh these details. If syncing fails, **Retry sync** retries
it and **Fix settings** opens Settings. A failed check keeps the previous successful
result visible alongside the error.

To add an address, open **Add IP**, tap **Get My IP**, optionally enter a name and
expiry, and tap **Add IP to CF**. You can also type an IPv4 address, IPv6 address, or
CIDR range yourself. IPv6 addresses without a prefix use `/64`; IPv4 uses `/32`.

In **List IPs**, tap an entry to edit its name or expiry, or hold it to delete it.
Leaving the name blank on the Add IP screen preserves an existing name. Added dates
are recorded for new entries; older entries may have an unknown added date.

### Custom IP checker

The app uses ipify by default. To use your own service, enable **Custom IP Checker
Site** on the Add IP screen, enter an HTTPS endpoint returning a JSON string field
named `IP`, and tap **Save Settings**. For rest-geoip, use the `/api/geoip` endpoint.

### Automatic updates

Enable **Auto-Update** in Settings to add the phone's current IP automatically.
Enable **Replace this phone's previous auto-added IP** to remove its previous
unnamed automatic entry when the address changes.

- Automatic IPv6 entries use `/64`; IPv4 entries use `/32`.
- Named entries are retained during replacement. Any expiry you explicitly set still applies.
- Existing manual entries and entries added by another phone are not adopted for replacement.
- Replacement tracking stays on this phone, even when names and dates are shared.

For faster checks when switching Wi-Fi/mobile networks or reconnecting, enable
**Monitor network changes in background**, tap **Save**, and allow notifications.
Its ongoing notification includes **Stop**, which returns the app to periodic checks.
Reopen the app after rebooting or force-stopping it.

Periodic checks remain as a fallback because a router's public IP can change without
a network-change event on the phone. Checks run approximately every 15 minutes or
later; connectivity and Android battery restrictions can delay them.

### Expiry

Choose **15 minutes**, **1 hour**, **1 day**, **1 month**, **Custom**, or **Never**.
One month means a calendar month, and dates display in your phone's time zone.

The app removes expired entries when it can run online. Removal can be delayed if
the phone is offline or Android restricts background work. For cleanup while phones
are offline, configure scheduled Worker cleanup below.

## Sharing names and dates across phones

Without Worker settings, details stay on the phone. To share names, added dates,
and expiry times through Cloudflare KV:

1. Create a KV namespace and a Worker in Cloudflare.
2. Deploy [CFWorker.js](CFWorker.js) to the Worker.
3. Add a KV binding named `kv_cfupdater`, pointing to the namespace.
4. Add an encrypted Worker secret named `API_SECRET` with a strong random value.
5. In the app's Settings, enter the Worker's HTTPS base URL and use that secret as
   the **Worker API key**. This is separate from your Cloudflare Access API token.
6. Use the same Worker URL/key and Account/Group IDs on each phone.

Open the IP list or refresh Status to fetch shared details. Changes save locally
first and retry syncing after failed requests. Shared changes may take a minute or
more to appear on another phone. Automatic updates pause if configured sync fails,
so they can check shared names before replacing an entry.

Local-only details and details for different Worker configurations are stored
separately. After enabling sync, re-enter any local names you want to share.
Anyone with the Worker key can edit shared details, including expiry dates, so give
it only to phones that should have that access. Changes to the shared Access group
affect all devices using its addresses.

### Scheduled cleanup

In the Worker's **Settings → Runtime variables and secrets**, add:

| Name | Type | Value |
| --- | --- | --- |
| `CF_ACCESS_TOKEN` | Secret | An account-scoped token with Access group read/edit access |
| `CF_ACCOUNT_ID` | Text | Your Account ID |
| `CF_GROUP_ID` | Text | The Access Group ID to clean up |

Under **Trigger events → Cron triggers**, add `*/5 * * * *` to check every five
minutes. Cleanup applies only to the configured group. Keep the existing
`API_SECRET` and KV binding. Do not set KV expiration on these records: removing
a record alone does not revoke the IP's Access permission.

### Sync authentication errors

HTTP 401 means the Worker rejected the request. Check that the app's **Worker API
key** matches the deployed Worker's `API_SECRET`, and that its Worker base URL is
correct. Entering the Access API token in this field will not work unless it was
also deliberately configured as that secret. Local edits stay saved while you fix
these settings.

# Roadmap

In no particular order, these are things I want to implement someday
- [x] Create release (and automate it, maybe)
- [x] List with all IPs right now in the Group
- [x] A name for each IP added through the app
- [x] Deletion of IPs from the app
- [x] Expiry date/time for each IP (needs you to re-open the app after that date/time)
- [x] Date added of each IP
- [x] App icon
- [x] Setup instructions inside the app
- [ ] Publish on F-Droid/Play Store
- [x] Auto-adding the current phone IP to the list
