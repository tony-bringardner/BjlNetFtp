#!/usr/bin/env bash
#
# FTPS interoperability check (BJL-2): this project's FTP server against non-Java clients.
#
#   lftp - GnuTLS, the TLS library FileZilla uses
#   curl - OpenSSL (or the platform's TLS on macOS)
#
# For each client: explicit FTPS (AUTH TLS) and implicit FTPS, passive and active mode,
# TLS 1.3 and TLS 1.2: list, download (checked byte for byte), upload (checked), resumed
# download (REST), and no TLS alerts or errors reported by the client.
#
# Usage:  src/test/interop/ftps-interop.sh      (KEEP_WORK=1 keeps the files and logs)
# Needs:  mvn, java, and lftp and/or curl on the PATH (or LFTP=/path/to/lftp, CURL=...).
#         A missing client is skipped. Exit status 0 = every check passed.
#
set -u
cd "$(dirname "$0")/../../.."
MVN=${MVN:-mvn}
LFTP=${LFTP:-$(command -v lftp || true)}
CURL=${CURL:-$(command -v curl || true)}
EXPLICIT_PORT=${EXPLICIT_PORT:-8160}
IMPLICIT_PORT=${IMPLICIT_PORT:-8161}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/ftps-interop.XXXXXX")
ROOT="$WORK/root"
PASS=0; FAIL=0; KNOWN=0; FAILED=()
# lftp retries forever on some errors; macOS has no timeout(1) unless coreutils is installed
TIMEOUT=$(command -v timeout || command -v gtimeout || true); [ -n "$TIMEOUT" ] && TIMEOUT="$TIMEOUT 60"

echo "Building test classes..."
$MVN -q -o test-compile dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" -Dspotbugs.skip >/dev/null 2>&1 \
  || $MVN -q test-compile dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt" -Dspotbugs.skip || { echo "build failed"; exit 2; }
CP="target/classes:target/test-classes:$(cat "$WORK/cp.txt")"

mkdir -p "$ROOT/pub" "$WORK/local"
head -c 1000000 /dev/urandom > "$ROOT/pub/big.bin"
echo "hello interop" > "$ROOT/pub/hello.txt"
head -c 300000 /dev/urandom > "$WORK/local/upload.bin"

# Server: runs until its stdin (this fifo) is closed
mkfifo "$WORK/stop"
java -cp "$CP" us.bringardner.net.ftp.test.InteropServer "$ROOT" $EXPLICIT_PORT $IMPLICIT_PORT < "$WORK/stop" > "$WORK/server.log" 2>&1 &
SERVER=$!
exec 3>"$WORK/stop"
cleanup() { exec 3>&-; wait $SERVER 2>/dev/null; if [ -n "${KEEP_WORK:-}" ]; then echo "kept $WORK"; else rm -rf "$WORK"; fi; }
trap cleanup EXIT
for i in $(seq 1 60); do grep -q READY "$WORK/server.log" && break; sleep 0.5; done
grep -q READY "$WORK/server.log" || { echo "server did not start:"; cat "$WORK/server.log"; exit 2; }

sum() { cksum < "$1" | cut -d' ' -f1-2; }

check() { # name, command output file, condition result
	local name=$1 ok=$2 log=$3
	if [ "$ok" = 0 ]; then PASS=$((PASS+1)); printf '  PASS  %s\n' "$name"
	else FAIL=$((FAIL+1)); FAILED+=("$name"); printf '  FAIL  %s\n' "$name"; sed 's/^/        | /' "$log" | tail -8; fi
}

# ---------------------------------------------------------------- lftp (GnuTLS)
if [ -n "$LFTP" ]; then
	echo "lftp: $("$LFTP" --version | head -1)"
	for mode in explicit implicit; do for pasv in on off; do for tls in 1.3 1.2; do
		name="lftp $mode passive=$pasv TLS$tls"
		if [ $mode = explicit ]; then url="ftp://127.0.0.1:$EXPLICIT_PORT"; else url="ftps://127.0.0.1:$IMPLICIT_PORT"; fi
		prio="NORMAL"; [ $tls = 1.2 ] && prio="NORMAL:-VERS-TLS1.3"
		up="up-lftp-$mode-$pasv-$tls.bin"
		dl="$WORK/local/dl-$mode-$pasv-$tls.bin"
		rm -f "$dl"
		head -c 400000 "$ROOT/pub/big.bin" > "$dl.part"
		log="$WORK/lftp-$mode-$pasv-$tls.log"
		$TIMEOUT "$LFTP" -c "set ssl:verify-certificate no; set ftp:ssl-force true; set ftp:ssl-protect-data true;
			set ftp:ssl-auth TLS; set ssl:priority $prio; set ftp:passive-mode $pasv; set net:max-retries 1; set net:timeout 10;
			open -u anonymous,x $url; cls -l pub; get pub/big.bin -o $dl; put $WORK/local/upload.bin -o pub/$up;
			get -c pub/big.bin -o $dl.part" > "$log" 2>&1
		rc=$?
		ok=1
		if [ $rc = 0 ] && ! grep -qi -E 'fatal|alert|error' "$log" \
			&& grep -q 'big.bin' "$log" \
			&& [ "$(sum "$dl")" = "$(sum "$ROOT/pub/big.bin")" ] \
			&& [ "$(sum "$ROOT/pub/$up")" = "$(sum "$WORK/local/upload.bin")" ] \
			&& [ "$(sum "$dl.part")" = "$(sum "$ROOT/pub/big.bin")" ]; then ok=0; fi
		check "$name" $ok "$log"
	done; done; done
else
	echo "lftp not found: skipped (install it, or set LFTP=...)"
fi

# ---------------------------------------------------------------- curl (OpenSSL)
# curl 7.x never reads from an upload's data connection after the TLS handshake, so the TLS 1.3
# NewSessionTicket Java sends there stays unread; closing a socket with unread data makes the
# kernel send a TCP reset, which discards the end of the upload, and the server replies 426.
# curl 8 reads it and passes. With curl 7, those uploads are reported as KNOWN, not FAIL.
if [ -n "$CURL" ]; then
	echo "curl: $("$CURL" --version | head -1)"
	curl_major=$("$CURL" --version | head -1 | sed 's/^curl \([0-9]*\).*/\1/')
	for mode in explicit implicit; do for pasv in on off; do for tls in 1.3 1.2; do
		name="curl $mode passive=$pasv TLS$tls"
		if [ $mode = explicit ]; then base="ftp://127.0.0.1:$EXPLICIT_PORT"; opts=(--ssl-reqd); else base="ftps://127.0.0.1:$IMPLICIT_PORT"; opts=(); fi
		[ $pasv = off ] && opts+=(-P -)
		if [ $tls = 1.2 ]; then opts+=(--tlsv1.2 --tls-max 1.2); else opts+=(--tlsv1.3); fi
		common=(-sS -k -u anonymous:x --max-time 60 "${opts[@]}")
		up="up-curl-$mode-$pasv-$tls.bin"
		dl="$WORK/local/curl-$mode-$pasv-$tls.bin"
		head -c 400000 "$ROOT/pub/big.bin" > "$dl.part"
		log="$WORK/curl-$mode-$pasv-$tls.log"
		: > "$log"
		step() { echo "== $*" >> "$log"; "$CURL" "${common[@]}" "$@" >> "$log" 2>&1; }

		ok=1; step "$base/pub/" && grep -q 'big.bin' "$log" && ok=0
		check "$name list" $ok "$log"
		ok=1; step -o "$dl" "$base/pub/big.bin" && [ "$(sum "$dl")" = "$(sum "$ROOT/pub/big.bin")" ] && ok=0
		check "$name download" $ok "$log"
		ok=1; step -C - -o "$dl.part" "$base/pub/big.bin" && [ "$(sum "$dl.part")" = "$(sum "$ROOT/pub/big.bin")" ] && ok=0
		check "$name resumed download" $ok "$log"
		ok=1; step -T "$WORK/local/upload.bin" "$base/pub/$up" && [ "$(sum "$ROOT/pub/$up")" = "$(sum "$WORK/local/upload.bin")" ] && ok=0
		if [ $ok != 0 ] && [ $tls = 1.3 ] && [ "${curl_major:-0}" -lt 8 ]; then
			KNOWN=$((KNOWN+1)); printf '  KNOWN %s upload (curl %s TLS 1.3 client bug, see the note in this script)\n' "$name" "$curl_major"
		else
			check "$name upload" $ok "$log"
		fi
		# curl -v reports when the data connection resumed the control connection's session
		# (curl 8 says so only in active mode; in passive mode it says nothing either way)
		ok=1; step -v -o /dev/null "$base/pub/hello.txt" && ok=0
		check "$name -v download" $ok "$log"
		if grep -qi -E 're-using|reusing' "$log"; then check "$name data connection resumed the TLS session" 0 "$log"
		elif [ "${curl_major:-0}" -ge 8 ] && [ $pasv = on ]; then printf '  N/A   %s data connection resumed the TLS session (curl 8 does not report it in passive mode)\n' "$name"
		else check "$name data connection resumed the TLS session" 1 "$log"; fi
	done; done; done
else
	echo "curl not found: skipped"
fi

echo
echo "$PASS passed, $FAIL failed$([ $KNOWN = 0 ] || echo ", $KNOWN known client bugs")"
for f in "${FAILED[@]+"${FAILED[@]}"}"; do echo "  failed: $f"; done
[ $FAIL = 0 ]
