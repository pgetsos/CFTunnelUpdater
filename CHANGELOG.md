# 0.7.0

- Add Status as the first tab with live IP/group status, monitoring, sync history, pending edits and next expiry.
- Add optional IP names and added/expiry dates shared through Cloudflare Worker KV, with offline retries.
- Add expiry presets and optional scheduled server cleanup.
- Add automatic IP replacement with /64 IPv6 ranges and protection for named entries; explicit expiry still applies.
- Add optional background network-change monitoring with an ongoing notification and Stop action.
- Add in-app setup instructions and a manually triggered signed release workflow.
- Update Android dependencies and build tools.

This release uses a new signing key. Installations signed with a different key must be uninstalled before installing this APK; preserve your settings first.
