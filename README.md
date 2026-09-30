# BjlNetFtp

The NetFtp project provides an implementation of the FTP protocol (client and server).

Requires Java 11 or later and Maven 3.6.3 or later. Depends on `bjl_file_system` and `bjl_net_framework` (which bring in `bjl_core` and `bjl_io`).

## Build

```
mvn verify
```

This compiles, runs the tests and runs SpotBugs. The build fails on any new SpotBugs finding. Accepted findings, each with its reason, are in `spotbugs-exclude.xml`.

## Server configuration

Each setting can be passed as a system property or set with the matching `FtpServer` setter.

| Property | Default | Meaning |
|---|---|---|
| `JFtp.port` | 21 | Control port (used by `FtpServer.main`) |
| `JFtp.root` | `/ftp` (`C:/ftp` on Windows) | FTP root directory |
| `JFtp.dataTimeout` | 600000 ms | A transfer that moves no data for this long is aborted with a 426 reply. This is also how long PASV waits for the client to connect. `setDataTimeout()` |
| `JFtp.connectTimeout` | 15000 ms | Connect timeout for active mode (PORT/EPRT). `setConnectTimeout()` |
| `JFtp.bufferSize` | 65536 bytes | Transfer buffer size. `setBufferSize()` |
| `JFtp.minControlPort` / `JFtp.maxControlPort` | 10333 / 65333 | Passive (PASV/EPSV) port range; busy ports are skipped |
| `JFtp.externalAddress` | control connection address | Address advertised in PASV replies (for NAT) |
| `JFtp.allowForeignDataAddress` | false | Allow PORT/EPRT to other hosts or ports below 1024, and PASV connections from other hosts (needed only for FXP). `setAllowForeignDataAddress()` |
| `JavaFtpServer.linger` | off | SO_LINGER (seconds) for data connections |

### Behaviour notes

- **Paths are confined to the user's root.** `..` stops at `/`, as in a chroot, and symbolic links that point outside the root are rejected.
- **STOR is safe to retry.** Data is written to a hidden temporary file (`.name.<id>.ftp-part`), which replaces the target only when the upload completes. APPE and STOR after REST append in place.
- **Transfer replies:**
  - A successful transfer gets `150` then `226`.
  - ABOR gets `426` then `226`.
  - A stalled or failed data connection gets `426`.
  - A local file system error gets `451`.
- **Logging** defaults to INFO.

## Client notes

- **PASV address:** `FtpClient` connects data connections to the control connection's host and ignores the address in the PASV reply. Call `setUsePasvAddress(true)` to use the PASV address instead.
- **Streams** returned by `getInputStream`/`getOutputStream` are buffered.
- **SO_LINGER** is off by default (`setCmdLinger`, `setTransferLinger`).
