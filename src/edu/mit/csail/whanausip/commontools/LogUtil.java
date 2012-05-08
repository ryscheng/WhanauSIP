package edu.mit.csail.whanausip.commontools;

import java.io.IOException;
import java.util.logging.*;

/**
 * Just sets a default way to get a Logger
 * 
 * @author ryscheng
 * @date 2010/05/20
 */
public class LogUtil {

	/**
	 * Returns a Logger object with the following name.
	 * Automatically prints all log entries to file
	 * 
	 * @param logName 	String 	= name of this logger instance
	 * @param filename 	String 	= filename/path to store entries into
	 * 							If null, stores to a default location
	 * @return Logger 			= object to log to
	 * @throws IOException
	 */
	public static Logger createLogger(String logName, String filename) throws IOException{
		Level level = WhanauDHTConstants.DEFAULT_LOG_LEVEL;
		Logger log = Logger.getLogger(logName);
		log.setLevel(level);
		
		//Create log file
		if (filename != null) {
			FileHandler fh = new FileHandler(filename, false);
			fh.setLevel(level);
			fh.setFormatter(new SimpleFormatter());
			log.addHandler(fh);
		}
		return log;
	}
}
