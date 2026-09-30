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
| `JFtp.loginFailureDelay` | 1000 ms | Delay before replying to a failed PASS/ACCT. `setLoginFailureDelay()` |
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
- **TLS (RFC 4217):**
  - **Explicit TLS:** `AUTH TLS` on the normal port. Data connections are clear until the client sends `PBSZ 0` and `PROT P`.
  - **Implicit TLS:** `setSecure(true)`. Data connections use TLS unless the client sends `PROT C`.
  - **PROT levels:** only `C` and `P` are supported; `S` and `E` get `536`.
- **Failed logins** wait `JFtp.loginFailureDelay` ms (default 1000) before the reply, and a connection is closed after 3 failures.

## Client notes

- **Passive mode:**
  - The client tries `EPSV` first (RFC 2428; needed for IPv6) and falls back to `PASV` for the rest of the session if the server doesn't support it (`setUseEpsv(false)` to always use PASV).
  - Data connections go to the control connection's host; `setUsePasvAddress(true)` uses the PASV reply's address instead.
- **Active mode** (`setActive(true)`): the client listens on the control connection's local address and sends `PORT` (IPv4) or `EPRT` (IPv6). With a protected data channel the client is the TLS client, as RFC 4217 requires.
- **One transfer per connection:** while a stream from `getInputStream`/`getOutputStream` is open, any other command on the same `FtpClient` throws `IOException`. Use one `FtpClient` per concurrent transfer.
- **Reconnect:** if the server has closed the control connection (e.g. idle timeout), the next command reconnects, logs in again (including AUTH), restores the current directory and retries safe commands (NOOP, PWD, CWD, TYPE, SIZE, …) once. Turn this off with `setAutoReconnect(false)`.
- **Streams** returned by `getInputStream`/`getOutputStream` are buffered.
- **SO_LINGER** is off by default (`setCmdLinger`, `setTransferLinger`).
- **TLS:** after logging in over TLS the client sends `PBSZ 0` / `PROT P`; if the server refuses, data connections are clear (`isDataChannelSecure()`). Every new connection negotiates AUTH again.
- **Reply timeouts:** if a transfer's final reply times out, the control connection is dropped and the next command reconnects.
- **Truncated downloads:** `close()` on a download that was read to the end throws if the server reports the transfer failed.
