package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import us.bringardner.net.ftp.client.ClientFtpResponse;
import us.bringardner.net.ftp.client.FtpClient;

/** Which replies mean "the data connection's TLS session wasn't resumed" (BJL-28). */
public class TestTlsRefusalReplies {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"522|SSL connection failed: session reuse required|true",          // vsftpd
		"425|Unable to build data connection: TLS session of data connection not resumed.|true", // FileZilla Server
		"450|TLS session resumption required|true",
		"451|Requested action aborted: local error|false",
		"425|Can't open data connection|false",
		"550|No such file|false" })
	public void recognizesResumptionRefusals(int code, String text, boolean expected) throws Exception {
		ClientFtpResponse res = new ClientFtpResponse();
		res.setResponseCode(code);
		Method setText = ClientFtpResponse.class.getMethod("setResponseText", String.class);
		setText.invoke(res, text);
		Method check = FtpClient.class.getDeclaredMethod("isTlsResumeRefusal", ClientFtpResponse.class);
		check.setAccessible(true);
		assertEquals(expected, check.invoke(null, res));
	}
}
