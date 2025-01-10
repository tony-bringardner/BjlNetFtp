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
 * ~version~V000.01.42-V000.01.37-V000.01.35-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 22, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpCommandFactory;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * FEAT FTP command (defined in RFC 2389)
 * 
 * @author Tony Bringardner
 */
public class Feat extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;
	private  Map<String, FeatCommand> supported = new TreeMap<String, FeatCommand>();
	
		
	public void addSupportedCommand(FeatCommand command){
		supported.put(command.getName(),command);
		FtpCommandFactory.addCommand(command);
	}
	

	public Feat() {
		super(FEAT);
	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.commands.BaseCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context)
		throws IOException {
		
		processor.reply(REPLY_211_SYSTEM_STATUS+"- Supported Extentions");
		Iterator<FeatCommand> it = supported.values().iterator();
		
		while( it.hasNext() ){
			FeatCommand cmd = (FeatCommand)it.next();
			String val = cmd.getFeatResponse(processor);
			if( val != null && val.length()>0 ){
				processor.reply(" "+cmd.getFeatResponse(processor));
			}
		}
		processor.reply(REPLY_211_SYSTEM_STATUS," End");
		

	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
}
