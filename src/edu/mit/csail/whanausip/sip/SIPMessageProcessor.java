package edu.mit.csail.whanausip.sip;

/**
 * Interface that process messages from the SIP layer
 * This includes log entries, as well as incoming text messages
 * 
 * @author ryscheng
 * @date 2010/07/15
 */
public interface SIPMessageProcessor
{
	/**
	 * Incoming SIP messages are sent to this processor
	 * Allows flexibility in how the messages are handled
	 * 
	 * @param sender	String = SIP URI of sender
	 * @param message	String = message contents
	 */
    public void processMessage(String sender, String message);
    
    /**
     * Log an error from the SIP layer
     * 
     * @param errorMessage String = Error message contents
     */
    public void processError(String errorMessage);
    
    /**
     * Log an INFO statement from the SIP layer
     * Used for logs about state of SIP layer
     * 
     * @param infoMessage String = informational message
     */
    public void processInfo(String infoMessage);
}
