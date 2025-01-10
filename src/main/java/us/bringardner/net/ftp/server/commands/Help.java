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
 * ~version~V000.01.35-V000.01.20-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandFactory;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommandFactory;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Help extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Help() {
		super(HELP);
		setHelp(""
				+ "	RFC 959\n"
				+ "	HELP (HELP)\n"
				+ "\n"
				+ "    This command shall cause the server to send helpful\n"
				+ "    information regarding its implementation status over the\n"
				+ "    control connection to the user.  The command may take an\n"
				+ "    argument (e.g., any command name) and return more specific\n"
				+ "    information as a response.  The reply is type 211 or 214.\n"
				+ "    It is suggested that HELP be allowed before entering a USER\n"
				+ "    command. The server may use this reply to specify\n"
				+ "    site-dependent parameters, e.g., in response to HELP SITE.\n"
				+ "");
	}

	/* 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if( !context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters.\nUsage: HELP COMMAND");
			return;
		}
		String name = context.getNextToken();
		ICommandFactory f = processor.getCommandFactory();
		if (!(f instanceof FtpCommandFactory)) {
			processor.reply(REPLY_502_NOT_IMPLEMENTED,"Unexpected command factory = "+f);
			return;
		} 
		FtpCommandFactory cf = (FtpCommandFactory) f;
		ICommand cmd = cf.getCommand(name);
		if( cmd == null ) {
			processor.reply(REPLY_504_NOT_IMP_FOR_PARAM,"Command "+name+" is not recignized");
			return;
		}

		String text = cmd.getHelp();
		
		processor.reply(REPLY_214_HELP,text);
		return;



	}

}
