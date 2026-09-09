# 0.7.2

The main features introduced in 0.7.0 are included in this release:

- A new Status tab shows your public IP, Access group status, background monitoring, shared details and next IP expiry.
- Give IPs a name and share their names, added dates and expiry dates across phones through Cloudflare Worker KV.
- Set an IP to expire after 15 minutes, an hour, a day, a month or at a custom date and time. Choose Never to keep it. A Worker schedule can remove expired IPs while your phone is offline.
- Automatically replace this phone's previous IP when it changes. IPv6 entries use /64 ranges. Named IPs are kept when replacing an old IP, but still expire if you set an expiry date.
- Check for IP changes when Wi-Fi or mobile networks change, with periodic checks as a fallback.
- Choose a light or dark theme in Settings.
- Follow the setup guide inside the app to configure Cloudflare access and shared details.
- Build and publish signed APKs from a manually started GitHub Actions workflow.
