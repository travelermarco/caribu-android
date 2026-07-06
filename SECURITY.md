# Security

Caribù Android bridges data from the Caribù PWA (running in Chrome on the same phone) to the Android Auto dashboard via a small local HTTP server (`CaribuBridgeServer`, `localhost:8888`).

## Known issue: the bridge server is not loopback-restricted

`CaribuBridgeServer` opens `ServerSocket(PORT)` with no explicit bind address, which in Java/Kotlin binds to **all network interfaces**, not just loopback. In practice this means: while your phone is on a Wi-Fi network (or acting as a hotspot), any other device on that same network can reach `http://<phone-ip>:8888`, not just the Chrome PWA running on the phone itself.

Suggested fix (not yet applied): bind explicitly to the loopback address, e.g. `ServerSocket(PORT, 50, InetAddress.getLoopbackAddress())`.

Until fixed, avoid relying on this app on untrusted networks (e.g. open/public Wi-Fi) if you'd rather other devices on that network not be able to reach the bridge endpoint.

## Reporting a vulnerability

If you find another security issue, please open a GitHub issue or contact the maintainer directly rather than disclosing it publicly first.
