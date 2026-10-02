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
 * ~version~V000.01.35-V000.01.11-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 27, 2006
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * OPTS FTP command (defined in RFC 2389)
 * @author Tony Bringardner
 * 
 */
public class Opts extends NoAuthReqBaseCommand implements FeatCommand {

	private static final long serialVersionUID = 1L;

	/** RFC 2640 feature name, also the OPTS option that turns it on. */
	public static final String UTF8 = "UTF8";

	public Opts() {
		super(OPTS);
	}

	/** 
	 *
<pre>
	 
7.9. OPTS parameters for MLST

   For the MLSx commands, the Client-FTP may specify a list of facts it
   wishes to be returned in all subsequent MLSx commands until another
   OPTS MLST command is sent.  The format is specified by:

        mlst-opts     = "OPTS" SP "MLST"
                        [ SP 1*( factname ";" ) ]

   By sending the "OPTS MLST" command, the client requests the server to
   include only the facts listed as arguments to the command in
   subsequent output from MLSx commands.  Facts not included in the
   "OPTS MLST" command MUST NOT be returned by the server.  Facts that
   are included should be returned for each entry returned from the MLSx
   command where they meaningfully apply.  Facts requested that are not
   supported, or which are inappropriate to the file or directory being
   listed should simply be omitted from the MLSx output.  This is not an
   error.  Note that where no factname arguments are present, the client
   is requesting that only the file names be returned.  In this case,
   and in any other case where no facts are included in the result, the
   space that separates the fact names and their values from the file
   name is still required.  That is, the first character of the output
   line will be a space, (or two characters will be spaces when the line
   is returned over the control connection) and the file name will start
   immediately thereafter.

   Clients should note that generating values for some facts can be
   possible, but very expensive, for some servers.  It is generally
   acceptable to retrieve any of the facts that the server offers as its
   default set before any "OPTS MLST" command has been given, however
   clients should use particular caution before requesting any facts not
   in that set.  That is, while other facts may be available from the
   server, clients should refrain from requesting such facts unless
   there is a particular operational requirement for that particular
   information, which ought be more significant than perhaps simply
   improving the information displayed to an end user.

   Note, there is no "OPTS MLSD" command, the fact names set with the
   "OPTS MLST" command apply to both MLST and MLSD commands.




Elz & Hethmon             [Expires March 2003]                 [Page 51]

Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002


   Servers are not required to accept "OPTS MLST" commands before
   authentication of the user-PI, but may choose to permit them.

7.9.1. OPTS MLST Response

   The "response-message" from [6] to a successful OPTS MLST command has
   the following syntax.

        mlst-opt-resp = "MLST OPTS" [ SP 1*( factname ";" ) ]

   This defines the "response-message" as used in the "opts-good"
   message in RFC2389 [6].

   The facts named in the response are those which the server will now
   include in MLST (and MLSD) response, after the processing of the
   "OPTS MLST" command.  Any facts from the request not supported by the
   server will be omitted from this response message.  If no facts will
   be included, the list of facts will be empty.  Note that the list of
   facts returned will be the same as those marked by a trailing
   asterisk ("*") in a subsequent FEAT command response.  There is no
   requirement that the order of the facts returned be the same as that
   in which they were requested, or that in which they will be listed in
   a FEAT command response, or that in which facts are returned in MLST
   responses.  The fixed string "MLST OPTS" in the response may be
   returned in any case, or mixture of cases.

7.9.2. Examples

 C> Feat
 S> 211- Features supported
 S>  MLST Type*;Size;Modify*;Perm;Unique;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End
 C> OptS Mlst Type;UNIX.mode;Perm;
 S> 200 MLST OPTS Type;Perm;UNIX.mode;
 C> Feat
 S> 211- Features supported
 S>  MLST Type*;Size;Modify;Perm*;Unique;UNIX.mode*;UNIX.chgd;X.hidden;
 S> 211 End
 C> opts MLst lang;type;charset;create;
 S> 200 MLST OPTS Type;
 C> Feat
 S> 211- Features supported
 S>  MLST Type*;Size;Modify;Perm;Unique;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End
 C> OPTS mlst size;frogs;
 S> 200 MLST OPTS Size;
 C> Feat
 S> 211- Features supported



Elz & Hethmon             [Expires March 2003]                 [Page 52]

Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002


 S>  MLST Type;Size*;Modify;Perm;Unique;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End
 C> opts MLst unique type;
 S> 501 Invalid MLST options
 C> Feat
 S> 211- Features supported
 S>  MLST Type;Size*;Modify;Perm;Unique;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End

   For the purposes of this example, features other than MLST have been
   deleted from the output to avoid clutter.  The example shows the
   initial default feature output for MLST.  The facts requested are
   then changed by the client.  The first change shows facts that are
   available from the server being selected.  Subsequent FEAT output
   shows the altered features as being returned.  The client then
   attempts to select some standard features which the server does not
   support.  This is not an error, however the server simply ignores the
   requests for unsupported features, as the FEAT output that follows
   shows.  Then, the client attempts to request a non-standard, and
   unsupported, feature.  The server ignores that, and selects only the
   supported features requested.  Lastly, the client sends a request
   containing a syntax error (spaces cannot appear in the factlist.)
   The server-FTP sends an error response and completely ignores the
   request, leaving the fact set selected as it had been previously.

   Note that in all cases, except the error response, the response lists
   the facts that have been selected.

 C> Feat
 S> 211- Features supported
 S>  MLST Type*;Size*;Modify*;Perm*;Unique*;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End
 C> Opts MLST
 S> 200 MLST OPTS
 C> Feat
 S> 211- Features supported
 S>  MLST Type;Size;Modify;Perm;Unique;UNIX.mode;UNIX.chgd;X.hidden;
 S> 211 End
 C> MLst tmp
 S> 250- Listing tmp
 S>   /tmp
 S> 250 End
 C> OPTS mlst unique;size;
 S> 200 MLST OPTS Size;Unique;
 C>  MLst tmp
 S> 250- Listing tmp
 S>  Unique=keVO1+YZ5; /tmp
 S> 250 End



Elz & Hethmon             [Expires March 2003]                 [Page 53]

Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002


 C> OPTS mlst unique;type;modify;
 S> 200 MLST OPTS Type;Modify;Unique;
 C> MLst tmp
 S> 250- Listing tmp
 S>  Type=dir;Modify=19990930152225;Unique=keVO1+YZ5; /tmp
 S> 250 End
 C> OPTS mlst fish;cakes;
 S> 200 MLST OPTS
 C> MLst tmp
 S> 250- Listing tmp
 S>   /tmp
 S> 250 End
 C> OptS Mlst Modify;Unique;
 S> 200 MLST OPTS Modify;Unique;
 C> MLst tmp
 S> 250- Listing tmp
 S>  Modify=19990930152225;Unique=keVO1+YZ5; /tmp
 S> 250 End
 C> opts MLst fish cakes;
 S> 501 Invalid MLST options
 C> MLst tmp
 S> 250- Listing tmp
 S>  Modify=19990930152225;Unique=keVO1+YZ5; /tmp
 S> 250 End

   This example shows the effect of changing the facts requested upon
   subsequent MLST commands.  Notice that a syntax error leaves the set
   of selected facts unchanged.  Also notice exactly two spaces
   preceding the pathname when no facts were selected, either
   deliberately, or because none of the facts requested were available.
</pre>
     * 
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		// RFC 2389: OPTS <command-name> [ SP <command-options> ]
		String args = context.getCommandLine().trim();
		int sp = args.indexOf(' ');
		args = sp < 0 ? "" : args.substring(sp+1).trim();
		if( args.isEmpty() ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM, "OPTS needs a command name");
			return;
		}
		sp = args.indexOf(' ');
		String name = (sp < 0 ? args : args.substring(0, sp)).toUpperCase(Locale.ROOT);
		String options = sp < 0 ? "" : args.substring(sp+1).trim();

		if( UTF8.equals(name) ) {
			utf8(processor, options);
		} else if( MLST.equals(name) ) {
			mlst(processor, options);
		} else {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM, "OPTS not supported for "+name);
		}
	}

	/**
	 * OPTS UTF8 ON (the de facto companion of RFC 2640's UTF8 feature, sent by FileZilla and
	 * others). Path names are always UTF-8 here, so ON is accepted and OFF is refused (BJL-50).
	 */
	private void utf8(FtpRequestProcessor processor, String options) throws IOException {
		if( options.isEmpty() || options.equalsIgnoreCase("ON") ) {
			processor.reply(REPLY_OK, "Always in UTF8 mode.");
		} else if( options.equalsIgnoreCase("OFF") ) {
			processor.reply(REPLY_504_NOT_IMP_FOR_PARAM, "UTF8 can't be turned off");
		} else {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM, "Expected OPTS UTF8 ON");
		}
	}

	/**
	 * OPTS MLST (RFC 3659 section 7.9): fact names are case-insensitive, unsupported facts are
	 * ignored, no fact list selects none, and a syntax error (a space in the list) leaves the
	 * selection unchanged. The reply lists the facts now selected (BJL-50).
	 */
	private void mlst(FtpRequestProcessor processor, String options) throws IOException {
		if( options.indexOf(' ') >= 0 ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM, "Invalid MLST options");
			return;
		}
		Map<String, Integer> supported = Mlst.getSupportedFacts();
		Map<String, Integer> wanted = new TreeMap<String, Integer>();
		for(String requested : options.split(";")) {
			for(Map.Entry<String, Integer> fact : supported.entrySet()) {
				if( fact.getKey().equalsIgnoreCase(requested.trim()) ) {
					wanted.put(fact.getKey(), fact.getValue());
				}
			}
		}
		StringBuilder reply = new StringBuilder("MLST OPTS");
		if( !wanted.isEmpty() ) {
			reply.append(' ');
			for(String fact : wanted.keySet()) {
				reply.append(fact).append(Mlst.SEPERATOR);
			}
		}
		processor.setTempValue(Mlst.FACTS, java.util.Collections.unmodifiableMap(wanted));
		processor.reply(REPLY_OK, reply.toString());
	}

	/**
	 * RFC 2640 section 3.1: a server that uses UTF-8 path names lists UTF8 in its FEAT reply.
	 */
	@Override
	public String getFeatResponse(FtpRequestProcessor processor) {
		return UTF8;
	}

}
