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
 * ~version~V000.01.54-V000.01.46-V000.01.37-V000.01.35-V000.01.18-V000.01.12-V000.01.11-V000.01.08-V000.01.07-V000.01.05-V000.00.03-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.core.util.ThreadSafeDateFormat;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Mlst  extends BaseCommand  implements FeatCommand {

	private static final long serialVersionUID = 1L;

	/*
	 * Symbolically, a time-val may be viewed as

        YYYYMMDDHHMMSS.sss

   The "." and subsequent digits ("sss") are optional.  However the "."
   MUST NOT appear unless at least one following digit also appears.

   Time values are always represented in UTC (GMT), and in the Gregorian
   calendar regardless of what calendar may have been in use at the date
   and time indicated at the location of the server-PI.

   The technical differences between GMT, TAI, UTC, UT1, UT2, etc, are
   not considered here.  A server-FTP process should always use the same
   time reference, so the times it returns will be consistent.  Clients
   are not expected to be time synchronized with the server, so the
   possible difference in times that might be reported by the different
   time standards is not considered important.

	 */
	public static final ThreadSafeDateFormat TIME_FORMAT = new ThreadSafeDateFormat("yyyyMMddHHmmss.SSS");

	/*
	 * 
	    size       -- Size in octets
        modify     -- Last modification time
        create     -- Creation time
        type       -- Entry type
        unique     -- Unique id of file/directory
        perm       -- File permissions, whether READ_PERMISSION, write, execute is
                      allowed for the login id.
        media-type -- MIME media-type of file contents per IANA registry.


   Servers are not required to support any particular set of the
   available facts.  However, servers SHOULD, if conceivably possible,
   support at least the type, perm, size, unique, and modify facts.


	 *
	 */

	private static Map<String, Integer> facts = new TreeMap<String, Integer>();

	public static final String FACTS = "FACTS";

	public static final int FACT_SIZE = 0;
	public static final int FACT_MODIFY = 1;
	public static final int FACT_TYPE = 2;
	public static final int FACT_PERM = 3;
	public static final int FACT_OWNER = 4;
	public static final int FACT_GROUP = 5;


	public static final String SEPERATOR = ";";

	static {
		//  These are the only supported facts
		facts.put(SIZE,(FACT_SIZE));
		facts.put(MODIFY,(FACT_MODIFY));
		facts.put(TYPE,(FACT_TYPE));
		facts.put(PERM,(FACT_PERM));
		facts.put(OWNER,(FACT_OWNER));
		facts.put(GROUP,(FACT_GROUP));
	}




	/**
	 * @return All supported FACTS for the MLS* commands.
	 */
	public static Map<String, Integer> getSupportedFacts() {
		return 	facts;
	}


	/**
	 * @param processor
	 * @return The FACTS that will be returned by the MLS* commands.  These may be alteres with the OPTS command. 
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Integer> getWantedFacts(FtpRequestProcessor processor) {
		Map<String, Integer> ret = (Map<String, Integer>)processor.getTempValue(FACTS);

		if( ret == null ){
			ret = getSupportedFacts();
		}

		return ret;
	}


	/**
	 * 
	 */
	public Mlst() {
		super(MLST);

	}

	/* 
	 * Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002
	 * http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt
	 * 
	 * MLST is a listing for a single file. It's much like LIST except
	 * the returned data is in a well defined system independent format.
	 * See the above document for a detailed description.
	 * 
	 * 
	 *  @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		FileSource target = null;

		if( context.hasNext() ){			
			//  file names may have white space so we have concatenate  all remaining tokens to get the name;
			String path = context.getRemainingTokens();
			target = processor.createNewFile(path);
		} else {
			target = processor.getCurrentDir();
		} 


		if( target == null || !target.exists()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 

	
		processor.reply(REPLY_250_FILE_ACTION_OK+"- Listing for "+target);
		processor.reply(" "+formatFile(target,processor));
		processor.reply(REPLY_250_FILE_ACTION_OK,"End");
	}

	public static String formatPerms(FileSource file, FtpRequestProcessor processor) throws IOException {
		if(!processor.getFtpRoot().isChildOfMine(file)) {
			return "";
		}else if( file.isDirectory()){
			return formatDirPerms(file,processor);
		} else {
			return formatFilePerms(file,processor);
		}
	}

	public static String formatFilePerms(FileSource file,FtpRequestProcessor processor) throws IOException {
		StringBuffer ret = new StringBuffer();
		if(file.canWrite()) {
			/*
			 * The "a" permission applies to objects of type=file, and indicates
			 * that the APPE (append) command may be applied to the file named.
			 */
			ret.append('a');
			/*
			  The "d" permission applies to all types.  It indicates that the
			   object named may be deleted, that is, that the RMD command may be
			   applied to it if it is a directory, and otherwise that the DELE
			   command may be applied to it.
			 */
			ret.append('d');

			/*
			   The "f" permission for objects indicates that the object named may be
			   renamed - that is, may be the object of an RNFR command.
			 */
			ret.append('f');


			/*
			   The "w" permission applies to type=file objects, and for some
			   systems, perhaps to other types of objects, and indicates that the
			   STOR command may be applied to the object named.
			 */
			ret.append('w');

		} 

		if( file.canRead() ){

			if( file.isDirectory()) {
				/*
		   		The "l" permission applies to the directory file types, and indicates
		   		that the listing commands, LIST, NLST, and MLSD may be applied to the
		   		directory in question.
				 */
				ret.append('l');
			}

			/*
		   The "r" permission applies to type=file objects, and for some
		   systems, perhaps to other types of objects, and indicates that the
		   RETR command may be applied to that object.
			 */
			ret.append('r');
		}




		return ret.toString();
	}

	public static String formatDirPerms(FileSource file,FtpRequestProcessor processor) throws IOException {
		StringBuffer ret = new StringBuffer();
		if( file.canWrite() ){

			/*
		   The "c" permission applies to objects of type=dir (and type=pdir,
		   type=cdir).  It indicates that files may be created in the directory
		   named.  That is, that a STOU command is likely to succeed, and that
		   STOR and APPE commands might succeed if the file named did not
		   previously exist, but is to be created in the directory object that
		   has the "c" permission.  It also indicates that the RNTO command is
		   likely to succeed for names in the directory.
			 */
			ret.append('c');

			/*
		   The "d" permission applies to all types.  It indicates that the
		   object named may be deleted, that is, that the RMD command may be
		   applied to it if it is a directory, and otherwise that the DELE
		   command may be applied to it.
			 */
			ret.append('d');

			/*
		   The "e" permission applies to the directory types.  When set on an
		   object of type=dir, type=cdir, or type=pdir it indicates that a CWD
		   command naming the object should succeed, and the user should be able
		   to enter the directory named.  For type=pdir it also indicates that
		   the CDUP command may succeed (if this particular pathname is the one
		   to which a CDUP would apply.)
			 */
			if(processor.getFtpRoot().isChildOfMine(file.getParentFile())){
				ret.append('e');
			}

			/*
		   The "f" permission for objects indicates that the object named may be
		   renamed - that is, may be the object of an RNFR command.
			 */
			ret.append('f');


			/*
		   The "m" permission applies to directory types, and indicates that the
		   MKD command may be used to create a new directory within the
		   directory under consideration.*/
			ret.append('m');

			/*
		   The "p" permission applies to directory types, and indicates that
		   objects in the directory may be deleted, or (stretching naming a
		   little) that the directory may be purged.  Note: it does not indicate
		   that the RMD command may be used to remove the directory named
		   itself, the "d" permission indicator indicates that.
			 */

			//  May need to traverse & make sure all children are writable
			ret.append('p');
		}



		if( file.canRead()){
			/*
			   The "l" permission applies to the directory file types, and indicates
			   that the listing commands, LIST, NLST, and MLSD may be applied to the
			   directory in question.
			 */
			ret.append('l');

		}


		return ret.toString();
	}

	public static String formatFile(FileSource file, FtpRequestProcessor processor) throws IOException{

		Map<String, Integer> factsWanted = getWantedFacts(processor);

		StringBuffer ret = new StringBuffer(" ");

		Iterator<String> it = factsWanted.keySet().iterator();

		while(it.hasNext()){
			String key = (String)it.next();
			String val = null;

			Integer factType = (Integer)factsWanted.get(key);

			switch(factType.intValue()){
			case FACT_GROUP: val = file.getGroup().getName();break;
			case FACT_OWNER: val = file.getOwner().getName();break;
			case FACT_SIZE:
				//if( file.isFile()) {
				val = (""+file.length());
				//}
				break;
			case FACT_PERM: val = (formatPerms(file, processor)); 
			break;
			case FACT_TYPE:
				if( file.isDirectory() ){
					FileSource cwd = processor.getCurrentDir();
					if( cwd.equals(file)){
						val = ("cdir");
					} else if( file.isChildOfMine(cwd)){
						val=("pdir");
					} else {
						val=("dir");
					}
				} else {
					val=("file");
				}
				break;
			case FACT_MODIFY: val=(TIME_FORMAT.format(new Date(file.lastModified()))); 
			break;
			default : throw new IllegalArgumentException("Invaid fact = "+factType+" for file "+file);
			}

			if( val != null ) {
				//  Ignore is not set (i.e. size for a dir)
				ret.append(key);
				ret.append("=");
				ret.append(val);
				ret.append(SEPERATOR);
			}

		}

		ret.append(' ');

		ret.append(file.getName());

		return ret.toString();

	}

	public static String formatTime(long time) {
		String ret = TIME_FORMAT.format(new Date(time));
		return ret;
	}

	/* 
	 * Changes with Opts commands
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse()
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {
		Map<String, Integer> supported = getSupportedFacts();
		Map<String, Integer> wanted = getWantedFacts(processor);

		StringBuffer buf = new StringBuffer(MLST);
		buf.append(' ');

		Iterator<String> it = supported.keySet().iterator();


		while(it.hasNext()){
			String key = (String)it.next();
			buf.append(key);
			if( wanted.containsKey(key)){
				buf.append('*');
			}
			buf.append(SEPERATOR);
		}

		return buf.toString();
	}
	
	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}

}
