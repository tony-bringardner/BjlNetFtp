# !/bin/bash
# Make a self-signed cert for testing

rm -f target/serverkeystore.p12
keytool -genkey -noprompt \
 -alias serverkey \
 -dname "CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, S=CCCC, C=DD" \
 -keystore target/serverkeystore.p12 \
 -storepass peekab00 \
 -keypass peekab00 \
 -keyalg RSA \
 -keysize 2048 \
 -sigalg SHA256withRSA
