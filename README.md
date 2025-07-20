# What is this app?

**CFTunnelUpdater** is, well, a **Cloudflare Tunnel Group** updater app. The point of the app is to quickly and easily add an IP to an Access Rule Group, in order to allow that IP to access services behind your Tunnel. This way it is extremely easy to work on a IP-whitelist way.

# How to setup your Cloudflare Tunnel
First of all, you have to create inside Access a Rule Group (Let's call it IP Group) with IP Ranges as a selector. 
Then, an Application with a Bypass policy (Action: Bypass). 
In the policy, assign the IP Group only (include option). 

You are done! Now any IP in this Group (and ONLY the IPs in this Group) can freely access your Application. Any other IP will be automatically blocked from Cloudflare.

# How to use the app
Opening the Cloudflare dashboard and navigating to the IP Group to add a new IP every time you connect through a new network or you restart your router, is obviously not that quick. This app helps adding a new IP in just a few seconds after the initial setup.

You will need:

### Your Account ID
You can find it at the bottom right of your domain page (API section)
### Your Group ID 
You can find it in the Rule Groups page, next to the name (far right of the page)
### An API Token
Go to your domain page, Manage account, Account API Tokens, Create Token, Create custom Token (the bottom option).
For permissions, you have to create 2: One with Account, Access: Organizations, Identity Providers, and Groups, Edit, and then one with Account, Access: Organizations, Identity Providers, and Groups, Read. Just add a name, Continue to summary, and get your API Token.

Add all 3 to the app. The app saves the last used IDs/Token so you will not have to add them again every time you add a new IP to the Group.

Then, when you open the app, it fills the IP input with the current IP your phone has. If for whatever reason this failed, you can click on the "Update IP" button to retry. You can erase the auto-filled IP and put any IP you would like (IPv4 and IPv6 are both supported).

Clicking "Add IP to CF" should add the IP to your Group, and give it access to your Applications behind the tunnel. A small Toast will appear on success! Each IP can be added only once, so don't worry about spaming the same IP multiple times in the Group!

# Roadmap
In no particular order, these are things I want to implement someday
- [ ] Create release (and automate it, maybe)
- [ ] List with all IPs right now in the Group
- [ ] A name for each IP added through the app
- [ ] Deletion of IPs from the app
- [ ] Expiry date/time for each IP (needs you to re-open the app after that date/time)
- [ ] Date added of each IP
- [ ] App icon
- [ ] Setup instructions inside the app
- [ ] Publish on F-Droid/Play Store
