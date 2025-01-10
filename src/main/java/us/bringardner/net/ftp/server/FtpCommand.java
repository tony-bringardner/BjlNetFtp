// ~version~V000.01.37-V000.01.35-V000.01.22-V000.01.05-V000.00.00-
/**
 FtpCommand.java

 Copyright 1998-2009 Tony Bringardner

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.   

*/


package us.bringardner.net.ftp.server;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.framework.server.Permission;

/**
 * @author Tony Bringardner
 *
 */
public interface FtpCommand extends ICommand {
	public static IPermission READ_PERMISSION = new Permission("READ");
	public static IPermission WRITE_PERMISSION = new Permission("WRITE");
	public static IPermission ADMIN_PERMISSION = new Permission("ADMIN");
	
	public void execute(FtpRequestProcessor processor, IRequestContext commandLine) throws IOException ;
	
}
