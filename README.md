# NetHacks
A couple of hacks for a rooted Android device for the network

- Allow redirecting DNS entries to a different port (prevent DNS hijacking by ISP)
- Enable or Disable packet forwarding using IPTables
- Get hosts from [Zackptg5's adblock](ttps://github.com/Zackptg5/Unified-Hosts-Adblock)

It REQUIRES Magisk to be installed for Root, in addition to the systemless hosts module to be enabled.

This app at the moment has 3 features:
- Adblock (Uses https://github.com/Zackptg5/Unified-Hosts-Adblock)
- Packet forwarding (A switch to enable/disable packet forwarding)
- Redirect DNS (Redirects DNS queries to a custom address, and port)

## Project Status

I have ceased development on this ever since DNSCrypt-Proxy was made available to android. DNSCrypt-Proxy allows for secure DNS querries to be made without the risk of hijacking due to the unencrypted dns protocol. Now, with Android 9.x (Pie), Google has a built-in method of DNSSec as well as Cloudflare's [1.1.1.1 app](https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone&hl=en_US)

I have also setup a [pi-hole](https://pi-hole.net/) on my network, making hosts-based adblock unneccessary. 
