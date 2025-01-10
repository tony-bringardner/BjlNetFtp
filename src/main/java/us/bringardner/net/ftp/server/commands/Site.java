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
 * ~version~V000.01.54-V000.01.37-V000.01.35-V000.01.28-V000.01.27-V000.01.22-V000.01.21-V000.01.20-V000.01.07-V000.01.05-V000.01.00-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Site  extends BaseCommand  implements FtpCommand {

	

	private static final long serialVersionUID = 1L;
	//  modDate is here to support a third party... don;t remember which :-(
	public static final String CMD_LAST_MOD_DATE = "modDate";
	public static final String CMD_CHMOD = "CHMOD";
	public static final String CMD_SET_FACTORY = "SetFactory";
	public static final String CMD_ROOT = "root";

	/**
	 * 
	 */
	public Site() {
		super(SITE);
		String help = 
				"\nAvailible commands:\n"
				+ "SITE "+CMD_LAST_MOD_DATE+" timeInMillis path\n"
				+ "\tChange a files last modification date\n"
				
				+ "SITE "+CMD_CHMOD+" NUMBER \n"
				+ "\tChange a files permissions\n"				
				+ "\tbased on unix numeric method. example: 644\n"
				
				+ "SITE "+CMD_ROOT+" path \n"
				+ "\tChange the root directory of the server.\n"				
				+ "\tAdmin rights are rquired.\n"
				
				+ "SITE "+CMD_SET_FACTORY+" id \n"
				+ "\tSet the FileSourceFactory for the server.\n"				
				+ "\tAdmin rights are rquired. "
				;

		setHelp(help);
	}

	/* 
	 * RFC 959
	 * SITE PARAMETERS (SITE)

            This command is used by the server to provide services
            specific to his system that are essential to file transfer
            but not sufficiently universal to be included as commands in
            the protocol.  The nature of these services and the
            specification of their syntax can be stated in a reply to
            the HELP SITE command.

	 * 
	 * Provide a way to switch the FileSource type
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if( !context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Please provide command and parameter...");
			return;
		}
		String cmd = context.getNextToken();


		//processor.logDebug("Site step1 command='"+cmd+"' args = "+commandLine);
		if(cmd.equalsIgnoreCase(CMD_LAST_MOD_DATE)) {
			setMdificationDate(processor, context);
			return;
		}
		if( cmd.equalsIgnoreCase(CMD_CHMOD)) {
			chmod(processor,context);
			return;
		}  

		if( !(cmd.equalsIgnoreCase(CMD_ROOT) || cmd.equalsIgnoreCase(CMD_SET_FACTORY))) {			
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Unknown SITE command = '"+cmd+"'");
		}
		
		// All others required admin rights
		if( !processor.isAuthorized(ADMIN_PERMISSION)) {
			processor.reply(REPLY_534_REQUEST_DENIED_FOR_POLICY_RESONS,"Permission denied for command '"+cmd+"'");
			return;
		}

		if( cmd.equalsIgnoreCase(CMD_ROOT)) {
			setRoot(processor,context);
		} else if( cmd.equalsIgnoreCase(CMD_SET_FACTORY)) {
			setFileSource(processor, context);
		} else {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Unknown SITE command = '"+cmd+"'");
		}
	}



	private void chmod(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		//site cmd arg1,arg2,...
		//SITE CHMOD 644 std.err
		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters. A three digit octal permission number is required.");
			return;
		}

		// Expected 3 octal digits 
		String number = context.getNextToken().trim();
		if( number.length()!=3 ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Expected a three digit octal number. Not "+number);
			return;
		}

		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters. Path is required.");
			return;
		}

		//  can't use the context because file names may have spaces
		String path = context.getRemainingTokens().trim();

		FileSource target = processor.createNewFile(path);
		if( target == null || !target.exists() ){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant file name ='"+path+"'");
			return;
		} 

		if(!target.canWrite()) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Permission denied.");
			return;
		}

		try {

			for(int idx = 0; idx < 3; idx++ ) {
				String str = ""+number.charAt(idx);
				int val = Integer.parseInt(str);
				int r = val & 4;
				int w = val & 2;
				int x = val & 1;
				switch (idx) {
				case 0: target.setOwnerReadable(r!=0);target.setOwnerWritable(w!=0);target.setOwnerExecutable(x!=0);break;
				case 1: target.setGroupReadable(r!=0);target.setGroupWritable(w!=0);target.setGroupExecutable(x!=0);break;
				case 2: target.setOtherReadable(r!=0);target.setOtherWritable(w!=0);target.setOtherExecutable(x!=0);break;
				}
			}
		} catch (Exception e) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Can't change permissions '"+e+"'");
			return;
		}

		processor.reply(REPLY_200_OK,path+" permissions set to "+number);
	}

	private void setFileSource(FtpRequestProcessor processor,IRequestContext context1) throws IOException {

		if(!context1.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}

		String id = context1.getNextToken();

		FileSourceFactory factory = processor.getFactory();

		FileSourceFactory factory2 = FileSourceFactory.getFileSourceFactory(id);
		processor.logDebug("SITE '"+CMD_SET_FACTORY+"' id = "+id+" result="+factory2);
		if( factory2 == null ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Invalid FileSourceFactory ='"+id);
			return;
		} else {
			processor.setFactory(factory);
		}

		processor.reply(REPLY_200_OK,"FileSourceFactory ="+factory.getTypeId());

	}

	private void setRoot(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}

		String path = context.getNextToken();


		FileSource newRoot = processor.getFactory().createFileSource(path);
		if( !newRoot.exists() || !newRoot.isDirectory()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,path+" is not an existing directory");
		} else {
			processor.setFtpRoot(newRoot);
			processor.logInfo("Set root to "+processor.getCurrentDir());
		}

		processor.reply(REPLY_200_OK,"Root is "+processor.getFtpRoot());
	}

	/**
	 * Modify the 'lastModification' time of a file.
	 * 
	 * @param processor
	 * @param parts of the command line
	 * @throws IOException
	 */
	private void setMdificationDate(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters.\nUSAGE SITE modDate timeInMillis path.");
			return;
		}

		long time = 0l;
		String tmp = context.getNextToken();

		try {
			time = Long.parseLong(tmp);
		} catch (Exception e) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Couldn't parse "+tmp+" as long \n"+e);
			return;	
		}

		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"No path provided.\nUSAGE SITE modDate timeInMillis path.");
			return;
		}

		String path = context.getNextToken();

		processor.logDebug("Site setModDate command='"+path+"'");
		FileSource target = processor.createNewFile(path);


		if( target == null || !target.exists() ){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant file name ='"+path+"'");
			return;
		} 

		if(!target.canWrite()) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Permission denied.");
			return;
		}

		try {
			target.setLastModifiedTime(time);	
		} catch (Exception e) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Could not change last mod time for '"+path+"'");
			return;
		}

		processor.reply(REPLY_213_FILE_STATUS,Mlst.formatTime(target.lastModified()));

	}
}
