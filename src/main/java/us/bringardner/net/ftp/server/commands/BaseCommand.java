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
 * ~version~V000.01.37-V000.01.35-V000.01.20-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;
import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public abstract class BaseCommand implements FtpCommand ,FTP {

	private static final long serialVersionUID = 1L;
	
	private String name ;
	private String help = "No help availibl";
	
	
	/**
	 * 
	 */
	public BaseCommand(String command) {
		this.name = command.toUpperCase();
		help = "No help availibl for "+name;		
	}

	@Override
	public String getName() {
		return name;
	}
	
	
	@Override
	public String getHelp() {
		return help;
	}
	
	
	/*
	 * Check authorization before processing.  Override if not required.
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#process(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	@Override
	public void execute(ICommandProcessor processor, IRequestContext context) throws IOException {
		
		execute((FtpRequestProcessor)processor,context);
		
	}
	
	
	
	public void setName(String name) {
		this.name = name;
	}

	public void setHelp(String help) {
		this.help = help;
	}

	
	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
	
	
}
