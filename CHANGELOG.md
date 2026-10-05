# Changelog

## 1.0.0 (unreleased)

The first release. This section covers the changes since the review that started on 2026-09-29.

Requires Java 11, `bjl_core` 1.3.1, `bjl_io` 1.1.1 and `bjl_net_framework` 1.1.0 (snapshots until
those are released). They are published to GitHub Packages and the pom names the repository.

### Security

- **FTPS clients check the server's host name.** `FtpClient` accepted any certificate its trust
  managers accepted, for any host, with both explicit (AUTH TLS) and implicit TLS. It now does the
  HTTPS host name check and sends SNI (`bjl_core`'s `TlsSockets`). `setVerifyHostname(false)` or the
  `VerifyHostname` property turns the check off for a server whose certificate doesn't match the name
  used to reach it. Data connections resume the control connection's TLS session and are unchanged.
- **No clear text login after AUTH TLS fails.** When the server accepted AUTH but TLS couldn't be set
  up, the client tried the next mechanism on the broken connection and, if the server refused it, logged
  in over the plain connection: a server or attacker in the middle could get the user name (and
  password) in clear text. A failed TLS setup after an accepted AUTH now closes the connection and
  `connect()` throws an `IOException`. A server that refuses AUTH still gets a plain login unless
  `setRequireSecure(true)` is set.
- **Passwords are kept out of logs.** The argument of a `PASS` command is replaced before it reaches
  a log or the session dialog.
- **Replies don't reveal the server's real paths.** Every reply goes through `hideRealPaths()`, so a
  message that quotes a path (an exception's message, say) shows the path the client sees, not where
  the user's root really is on the server.
- **Symbolic links and the user's root (BJL-17).** A path is resolved inside the user's root, and
  `FtpServer.SymlinkPolicy` decides where a link may point. With `STRICT`, the default, its real path
  must also be inside the root, so a link can't be used to reach other files.

### Server

- The `150` reply is sent before the transfer thread starts. A small transfer could finish, and send
  its `226`, before the `150`, which confused clients.
- An unexpected close of the control connection works like `ABOR` (RFC 959): a running transfer is
  stopped and passive listeners and data sockets are released, so no ports, threads or files stay open.
- The passive (PASV / EPSV) listener is closed after its one connection; it used to stay open.
- Active mode (PORT / EPRT) connects with a connect timeout. The timeout used to be passed as the local
  port, so the server tried to bind port 10.
- `SITE` sent two replies to one command, and text with line breaks went out as extra lines without a
  reply code. Multi-line text is sent as an RFC 959 multi-line reply, and each command gets one reply (BJL-26).
- Accounts (BJL-4): a login that needs an account gets `530` (it was `332`, which says the password
  was accepted), and the password is kept for one `ACCT`, which retries the login as `user@account`.
- The greeting is `220` (it was `200`, which curl rejects; BJL-30).
- `LIST` dates are written the way `ls` writes them (BJL-45): English month names whatever the server's
  locale, the day padded with a space ("Oct  1"), the time for files changed in the last six months and
  the year for older (and future) ones.
- `MLSx` `modify=` and `MDTM` times are written as `YYYYMMDDHHMMSS.sss` (BJL-36) in UTC, as RFC 3659
  requires; they used to be in the JVM's time zone, so clients saw times shifted by the server's UTC
  offset (BJL-37). `LIST` dates stay in local time, like `ls`.
- `MLST` sends the whole path name (BJL-48), `FEAT` lists `UTF8` (RFC 2640) and `OPTS MLST` is supported
  (BJL-50).
- TLS data connections end with just `close_notify`. Java's close of a TLS 1.3 socket sends a
  `user_canceled` alert first, which GnuTLS clients such as FileZilla and lftp report as a fatal error
  on every listing and download (BJL-2).
- Replies, and the session's transfer threads, use locks instead of `synchronized`, so a session on a
  virtual thread doesn't hold on to its carrier thread while a reply waits for a slow client (Java
  21-23, BJL-52). Transfer threads are started and managed by `bjl_net_framework`'s server (BJL-60).

### Client

- Passive mode connects to the control connection's host and ignores the address in the PASV reply by
  default, like curl's `--ftp-skip-pasv-ip`: servers behind NAT often send a private address.
- Active mode listens before sending `PORT` / `EPRT` (servers may connect as soon as they get it), and
  it used to send `PORT` with no address, which servers reject. `EPRT` is used for IPv6 (RFC 2428).
- Data connections resume the control connection's TLS session (BJL-18), which servers such as vsftpd
  (`require_ssl_reuse`) and FileZilla Server require. In active mode TLS starts after the `1xx` reply, so
  a refused session can be retried (BJL-28), and a `522` reply after the handshake is handled.
- Passive mode connects the data connection before sending the transfer command: servers such as vsftpd
  wait for it before they send the `1xx` reply.
- `TYPE` is sent only when it changes, not before every transfer, one round trip less per file (BJL-34).
- If the client loses track of where the control connection is in the conversation, it drops the
  connection, so the next command reconnects instead of reading the wrong reply.
- Listing parsing: `MLSx` entries are split into facts and path name as RFC 3659 section 7.2 says (a
  path name may contain spaces or `;`, a fact a space), names with non-ASCII owners or groups are no longer
  garbled, and address octets over 127 are no longer negative.
- Commands and data connections use locks instead of `synchronized`, so many clients used from virtual
  threads don't stall on Java 21-23 (BJL-58).
- `ListEntry`: the LIST / MLSx entry parsing, public and shared with `bjl_file_system_ftp`'s `FtpFile`, which
  had its own copy (with the old non-ASCII bug). `FtpClientFile.splitMlsxEntry` and `mlsxName` are
  deprecated pass-throughs to it.
- `getLastActivity()` was always 0 (its field was never updated); it now says when the control
  connection was last used.

### Performance

- Directory listings go out through one 64 KB buffer that is flushed once (BJL-30), and multi-line
  replies such as `FEAT` are sent with one flush (BJL-35), not one TCP segment or TLS record per line.
- Link targets allowed by the server are resolved once per session, not for every file checked, such as
  every entry of an `MLSD` listing (BJL-32).
- Socket buffers are sized by the operating system (TCP auto tuning) unless a size is configured, and are
  set before connecting so window scaling applies (BJL-29).

### Changed (may need a code change)

- An FTPS client connecting to a server whose certificate doesn't match the host name now fails; use
  `setVerifyHostname(false)` for such a server.
- An FTPS client whose server accepts AUTH TLS but whose TLS fails gets an `IOException` from `connect()`
  instead of continuing without TLS.
- `MLSx` and `MDTM` times are UTC, not the server's local time.
