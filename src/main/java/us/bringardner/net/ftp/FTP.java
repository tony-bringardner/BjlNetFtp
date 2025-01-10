/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.01.56-V000.01.37-V000.01.11-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp;


/**
 * @author Tony Bringardner
 *
 */
public interface FTP {
	
	/*
	RFC 959                                                     October 1985
	File Transfer Protocol

	      5.3.1.  FTP COMMANDS

	         The following are the FTP commands:

	            USER <SP> <username> <CRLF>
	            PASS <SP> <password> <CRLF>
	            ACCT <SP> <account-information> <CRLF>
	            CWD  <SP> <pathname> <CRLF>
	            CDUP <CRLF>
	            SMNT <SP> <pathname> <CRLF>
	            QUIT <CRLF>
	            REIN <CRLF>
	            PORT <SP> <host-port> <CRLF>
	            PASV <CRLF>
	            TYPE <SP> <type-code> <CRLF>
	            STRU <SP> <structure-code> <CRLF>
	            MODE <SP> <mode-code> <CRLF>
	            RETR <SP> <pathname> <CRLF>
	            STOR <SP> <pathname> <CRLF>
	            STOU <CRLF>
	            APPE <SP> <pathname> <CRLF>
	            ALLO <SP> <decimal-integer>
	                [<SP> R <SP> <decimal-integer>] <CRLF>
	            REST <SP> <marker> <CRLF>
	            RNFR <SP> <pathname> <CRLF>
	            RNTO <SP> <pathname> <CRLF>
	            ABOR <CRLF>
	            DELE <SP> <pathname> <CRLF>
	            RMD  <SP> <pathname> <CRLF>
	            MKD  <SP> <pathname> <CRLF>
	            PWD  <CRLF>
	            LIST [<SP> <pathname>] <CRLF>
	            NLST [<SP> <pathname>] <CRLF>
	            SITE <SP> <string> <CRLF>
	            SYST <CRLF>
	            STAT [<SP> <pathname>] <CRLF>
	            HELP [<SP> <string>] <CRLF>
	            NOOP <CRLF>

*/
    public static final int FTP_PORT = 21;
    public static final int FTP_SSL_PORT = 421;
    public static final String TYPE_ASCII = "A";
    public static final String TYPE_IMAGE = "I";
    
	//  Commands
    public static final String ACCT = "ACCT";
    public static final String ABOR = "ABOR";	
	public static final String APPE = "APPE";
	
	public static final String CDUP = "CDUP";
	public static final String COMPRESSION = "COMPRESSION";
	public static final String CUP  = "CUP";
	public static final String CWD  = "CWD";
	public static final String DELE = "DELE";
	
	public static final String FEAT = "FEAT";
	public static final String HELP = "HELP";
	public static final String LIST = "LIST";
	public static final String MKD  = "MKD";
	public static final String MLSD = "MLSD";
	public static final String MLST = "MLST";
	public static final String MDTM = "MDTM";
	public static final String MODE = "MODE";
	public static final String NOOP = "NOOP";
	public static final String NLST = "NLST";
    public static final String OPTS = "OPTS";
	public static final String PASS = "PASS";
	public static final String PASV = "PASV";
	public static final String PORT = "PORT";
	public static final String PWD  = "PWD";
	public static final String QUIT = "QUIT";
	public static final String RMD  = "RMD";
	public static final String REST = "REST";
	public static final String RNFR = "RNFR";
	public static final String RNTO = "RNTO";
	public static final String RETR = "RETR";
	public static final String SIZE = "SIZE";
	
	public static final String STRU = "STRU";
	public static final String STOR = "STOR";
	public static final String SITE = "SITE";	
	public static final String SYST = "SYST";
	public static final String TYPE = "TYPE";	
	public static final String TVFS = "TVFS";
	public static final String USER = "USER";

	public static final String EPRT = "EPRT";
	public static final String EPSV = "EPSV";

	//  These are required for the MLST/MLSD commands
	public static final String MODIFY = "MODIFY";
	public static final String PERM = "PERM";
	public static final String OWNER = "Unix.owner";
	public static final String GROUP = "Unix.group";

	
	//  Non - RFC values.
	public static final String DEBUG = "DEBUG"; 
	public static int MODE_ACTIVE = 0;
	public static int MODE_PASSIVE = 1;
	
	
	//  Misc constants
	public static final String ANONYMOUS = "ANONYMOUS";
	public static final String FTP_USER  = "FTP";
	public static final String NOT_IMPLEMENTED = "not implemented.";
	

	public static final int REPLY_OK = 200; // Command okay.
	public static final int REPLY_200_OK = 200; // Command okay.
	public static final int REPLY_500_SYNTAX_ERROR = 500; //Syntax error, command unrecognized.	  This may include errors such as command line too long.
	public static final int REPLY_501_SYNTAXT_ERROR_IN_PARAM=501; // Syntax error in parameters or arguments.
	public static final int REPLY_502_NOT_IMPLEMENTED = 502; // Command not implemented.
	public static final int REPLY_503_BAD_SEQ_OF_COMMANDS = 503;// Bad sequence of commands.
	public static final int REPLY_504_NOT_IMP_FOR_PARAM = 504;// Command not implemented for that parameter.

	/*
    In this case, the text is exact and not left to the
    particular implementation; it must read:
         MARK yyyy = mmmm
    Where yyyy is User-process data stream marker, and mmmm
    server's equivalent marker (note the spaces between markers
    and "=").
    */

	public static final int REPLY_110_RESTART = 110;// Restart marker reply.
	public static final int REPLY_211_SYSTEM_STATUS    = 211;// System status, or system help reply.
	public static final int REPLY_212_DIR_STATUS 	  = 212; // Directory status.
	public static final int REPLY_213_FILE_STATUS 		= 213; // File status.
	
	/*
	 On how to use the server or the meaning of a particular
        non-standard command.  This reply is useful only to the
        human user.
	 */
	public static final int REPLY_214_HELP 		= 214; // Help message.
        
	/*
	 Where NAME is an official system name from the list in the
        Assigned Numbers document.
	 */
	public static final int REPLY_215_NAME = 215; // NAME system type.
        
	public static final int REPLY_120_READY = 120;// Service ready in nnn minutes.
	public static final int REPLY_220_READY_NEW_USER = 220;// Service ready for new user.
	public static final int REPLY_221_CLOSING_CONTROL =  221; // Service closing control connection. Logged out if appropriate.
	public static final int REPLY_421_SERVICE_NOT_AVAILIBLE = 421; // Service not available, closing control connection.        This may be a reply to any command if the service knows it        must shut down.
	public static final int REPLY_125_DATA_CON_ALREADY_OPEN = 125; // Data connection already open; transfer starting.
	public static final int REPLY_225_DATA_CON_OPEN = 225; // Data connection open; no transfer in progress.
	public static final int REPLY_425_CANT_OPEN_DATA_CON = 425; // Can't open data connection.
	public static final int REPLY_226_CLOSING_DATA_CON = 226; // Closing data connection.        Requested file action successful (for example, file        transfer or file abort).
	public static final int REPLY_426_CON_CLOSED =  426;// Connection closed; transfer aborted.
	public static final int REPLY_227_ENTERING_PASSIVE_MODE = 227; // Entering Passive Mode (h1,h2,h3,h4,p1,p2).

	public static final int REPLY_230_USER_LOGGED_IN = 230;// User logged in, proceed.
	public static final int REPLY_530_USER_NOT_LOGGED_IN = 530; // Not logged in.
	public static final int REPLY_533_USER_NOT_AUTHORIZED = 533; // IN not defined in RFC 595 so I created one
	public static final int REPLY_331_USER_NAME_OK_NEED_PASS = 331; // User name okay, need password.
	public static final int REPLY_332_NEED_ACCOUNT = 332; // Need account for login.
	public static final int REPLY_532_NEED_ACCOUNT_TO_STORE_FILES =532;// Need account for storing files.

	public static final int REPLY_150_FILE_STATUS_OK=150;// File status okay; about to open data connection.
	public static final int REPLY_250_FILE_ACTION_OK = 250;// Requested file action okay, completed.
	public static final int REPLY_257_PATHNAME_CREATED = 257;// "PATHNAME" created.
	public static final int REPLY_350_FILE_ACTION_PENDING = 350;// Requested file action pending further information.
	public static final int REPLY_450_FILE_ACTION_FAILED = 450;// Requested file action not taken.   File unavailable (e.g., file busy).
	public static final int REPLY_550_ACTION_NOT_TAKEN = 550;// Requested action not taken.        File unavailable (e.g., file not found, no access).
	
	public static final int REPLY_551_ACTION_ABORTED = 551;// Requested action aborted. Page type unknown.
	/*
    452 Requested action not taken.
        Insufficient storage space in system.
    552 Requested file action aborted.
        Exceeded storage allocation (for current directory or
        dataset).
    553 Requested action not taken.
        File name not allowed.

 4.2.2 Numeric  Order List of Reply Codes

    110 Restart marker reply.
        In this case, the text is exact and not left to the
        particular implementation; it must read:
             MARK yyyy = mmmm
        Where yyyy is User-process data stream marker, and mmmm
        server's equivalent marker (note the spaces between markers
        and "=").
    120 Service ready in nnn minutes.
    125 Data connection already open; transfer starting.
    150 File status okay; about to open data connection.

RFC 959                                                     October 1985
File Transfer Protocol

    200 Command okay.
    202 Command not implemented, superfluous at this site.
    211 System status, or system help reply.
    212 Directory status.
    213 File status.
    214 Help message.
        On how to use the server or the meaning of a particular
        non-standard command.  This reply is useful only to the
        human user.
    215 NAME system type.
        Where NAME is an official system name from the list in the
        Assigned Numbers document.
    220 Service ready for new user.
    221 Service closing control connection.
        Logged out if appropriate.
    225 Data connection open; no transfer in progress.
    226 Closing data connection.
        Requested file action successful (for example, file
        transfer or file abort).
    227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).
    230 User logged in, proceed.
    250 Requested file action okay, completed.
    257 "PATHNAME" created.

    331 User name okay, need password.
    332 Need account for login.
    350 Requested file action pending further information.

    421 Service not available, closing control connection.
        This may be a reply to any command if the service knows it
        must shut down.
    425 Can't open data connection.
    426 Connection closed; transfer aborted.
    450 Requested file action not taken.
        File unavailable (e.g., file busy).
    451 Requested action aborted: local error in processing.
    452 Requested action not taken.
        Insufficient storage space in system.

RFC 959                                                     October 1985
File Transfer Protocol

	*/

	
	/* Added AUTH spec
	 * RFC 2228                FTP Security Extensions             October 1997

	 * The following new optional commands are introduced in this   specification:

      AUTH (Authentication/Security Mechanism),
      ADAT (Authentication/Security Data),
      PROT (Data Channel Protection Level),
      PBSZ (Protection Buffer Size),
      CCC (Clear Command Channel),
      MIC (Integrity Protected Command),
      CONF (Confidentiality Protected Command), and
      ENC (Privacy Protected Command).

	 */

	public static final String AUTH = "AUTH";
	public static final String ADAT = "ADAT";
	public static final String PROT = "PROT";
	public static final String PBSZ = "PBSZ";
	public static final String CCC = "CCC";
	public static final String MIC = "MIC";
	public static final String CONF = "CONF";
	public static final String ENC = "ENC";

	/*
         C - Clear
         S - Safe
         E - Confidential
         P - Private

	 */
	public static final String DATA_CHANNEL_PROTECTION_LEVEL_CLEAR="C"; 
	public static final String DATA_CHANNEL_PROTECTION_LEVEL_SAFE="S";
	public static final String DATA_CHANNEL_PROTECTION_LEVEL_CONFIDENTIAL="E";
	public static final String DATA_CHANNEL_PROTECTION_LEVEL_PRIVATE="P";
	
	
	/*
5.  New FTP Replies

   The new reply codes are divided into two classes.  The first class is
   new replies made necessary by the new FTP Security commands.  The
   second class is a new reply type to indicate protected replies.

   5.1.  New individual reply codes

      232 User logged in, authorized by security data exchange.
      */
	public static final int REPLY_232_LOGGED_IN_AUTHORIZED_BY_DATA_EXCHANGE = 232;
	/*
      234 Security data exchange complete.
      */
	public static final int REPLY_234_SECURITY_DATA_EXCHANGE_COMPLETE=234;
	
	/*
      235 [ADAT=base64data]
            ; This reply indicates that the security data exchange
            ; completed successfully.  The square brackets are not
            ; to be included in the reply, but indicate that
            ; security data in the reply is optional.
    */
	public static final int REPLY_235_SECURITY_DATA_EXCHANGE_COMPLETE_MAY_RETURN_ADAT=235;
	
	/*

      334 [ADAT=base64data]
            ; This reply indicates that the requested security mechanism
            ; is ok, and includes security data to be used by the client
            ; to construct the next command.  The square brackets are not
            ; to be included in the reply, but indicate that
            ; security data in the reply is optional.
      */
	public static final int REPLY_334_SEC_MECH_OK_MAY_RETURN_ADAT=334;
	
	/*
      335 [ADAT=base64data]
            ; This reply indicates that the security data is
            ; acceptable, and more is required to complete the
            ; security data exchange.  The square brackets
            ; are not to be included in the reply, but indicate
            ; that security data in the reply is optional.
    */
	public static final int REPLY_335_SEC_DATA_ACCEPTED_MAY_RETURN_ADTA=335;
	
	/*
      336 Username okay, need password.  Challenge is "...."
            ; The exact representation of the challenge should be chosen
            ; by the mechanism to be sensible to the human user of the
            ; system.

	*/
	public static final int REPLY_335_USERNAME_OK_NEED_PW_CHALLANGE_IS=335;
	
	/*
      431 Need some unavailable resource to process security.
      */
	public static final int REPLY_431_NEED_UNAVILIBLE_RESOURCE_TO_PROCESS=431;
	
	/*
      533 Command protection level denied for policy reasons.
      */
	public static final int REPLY_533_COMMAND_PROTECTION_LEVEL_DENIED_FOR_POLICY_REASONS=533;
	
	/*
      534 Request denied for policy reasons.
      */
	public static final int REPLY_534_REQUEST_DENIED_FOR_POLICY_RESONS=534;
	
	/*
      535 Failed security check (hash, sequence, etc).
      */
	public static final int REPLY_535_FAILED_SECURITY_CHECK=535;
	
	/*
      536 Requested PROT level not supported by mechanism.
      */
	public static final int REPLY_536_PROT_LEVEL_NOT_SUPPORTED_BY_MECH=536;
	
	/*
      537 Command protection level not supported by security mechanism.
      */
	public static final int REPLY_537_COMMAND_PROTECTION_LEVEL_NOT_SUPPORTED_BY_SEC_MECH=537;
	
	/*
   5.2.  Protected replies.

      One new reply type is introduced:

         6yz   Protected reply

            There are three reply codes of this type.  The first, reply
            code 631 indicates an integrity protected reply.  The
            second, reply code 632, indicates a confidentiality and
            integrity protected reply.  the third, reply code 633,
            indicates a confidentiality protected reply.

            The text part of a 631 reply is a Telnet string consisting
            of a base 64 encoded "safe" message produced by a security
            mechanism specific message integrity procedure.  The text
            part of a 632 reply is a Telnet string consisting of a base
            64 encoded "private" message produced by a security
            mechanism specific message confidentiality and integrity
            procedure.  The text part of a 633 reply is a Telnet string
            consisting of a base 64 encoded "confidential" message
            produced by a security mechanism specific message
            confidentiality procedure.

            The client will decode and verify the encoded reply.  How
            failures decoding or verifying replies are handled is
            implementation-specific.  An end-of-line code need not be
            included, but if one is included, it must be a Telnet end-
            of-line code, not a local end-of-line code.

            A protected reply may only be sent if a security data
            exchange has succeeded.

            The 63z reply may be a multiline reply.  In this case, the
            plaintext reply must be broken up into a number of
            fragments.  Each fragment must be protected, then base 64
            encoded in order into a separate line of the multiline
            reply.  There need not be any correspondence between the
            line breaks in the plaintext reply and the encoded reply.
            Telnet end-of-line codes must appear in the plaintext of the
            encoded reply, except for the final end-of-line code, which
            is optional.

            The multiline reply must be formatted more strictly than the
            continuation specification in RFC 959.  In particular, each
            line before the last must be formed by the reply code,
            followed immediately by a hyphen, followed by a base 64
            encoded fragment of the reply.

            For example, if the plaintext reply is

               123-First line
               Second line
                 234 A line beginning with numbers
               123 The last line

            then the resulting protected reply could be any of the
            following (the first example has a line break only to fit
            within the margins):
            
     */
	
	/*

  631 base64(protect("123-First line\r\nSecond line\r\n  234 A line
  631-base64(protect("123-First line\r\n"))
  631-base64(protect("Second line\r\n"))
  631-base64(protect("  234 A line beginning with numbers\r\n"))
  631 base64(protect("123 The last line"))
  631-base64(protect("123-First line\r\nSecond line\r\n  234 A line b"))
  631 base64(protect("eginning with numbers\r\n123 The last line\r\n"))
	 
	 */
	public static final int REPLY_631_INTEGRITY_PROTECTION_REPLY=631;
	public static final int REPLY_632_INTEGRITY_AND_CONFIDENTIALITY_PROTECTION_REPLY=632;
	public static final int REPLY_633_CONFIDENTIALITY_PROTECTION_REPLY=633;

}
