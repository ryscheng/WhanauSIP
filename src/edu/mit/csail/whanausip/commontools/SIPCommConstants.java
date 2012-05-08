package edu.mit.csail.whanausip.commontools;

/**
 * Constants for WhanauSIP in SIP Communicator
 * 
 * @author ryscheng
 * @date 2010/06/19
 */
public class SIPCommConstants {
	/**
	 * Protocol Name - used to register services in Felix
	 */
	public static final String 	PROTOCOL_NAME 			= "WhanauSIP";
	
	/**
	 * Protocol Description - displayed in GUI
	 */
	public static final String 	PROTOCOL_DESCRIPTION 	= "P2P-SIP Using a Sybil-Proof DHT";
	
	/**
	 * Key where the port WhanauDHT listens on, is stored in accountProperties
	 */
	public static final String 	TXT_WHANAU_PORT 		= "WHANAU_PORT";
	
	/**
	 * Key where the absolute path of the keystore, is stored in accountProperties
	 */
	public static final String 	TXT_KEYS_PATH 			= "KEYS_PATH";
	
	/**
	 * Name of the buddy list group that stores the WhanauDHT peers
	 */
	public static final String 	PEER_GROUP_NAME 		= "WhanauSIP Peers";
	
	/**
	 * The period between successive calls to setup() in the DHT (in milliseconds)
	 * Currently set to 1 minutes
	 */
	public static final long 	SETUP_PERIOD 			= 60000;
	
	/**
	 * WhanauSetupTimerTask.run() runs every SETUP_PERIOD/SETUP_TIMER_DIVIDER milliseconds
	 * This is so that node.setSetupStage(0) is run SETUP_PERIOD/SETUP_TIMER_DIVIDER 
	 * milliseconds before setup()
	 */
	public static final long	SETUP_TIMER_DIVIDER		= 6;
	
	/**
	 * The default port that WhanauDHT listens on.
	 * WhanauDHT takes up 3 ports after this number (ie. 9009-9011)
	 */
	public static final int 	DEFAULT_WHANAUDHT_PORT 	= 9009;
	
	/**
	 * The default SIP address to store in a contact when none is provided
	 */
	public static final String 	DEFAULT_SIPADDRESS 		= "sip:badaddress@google.com";
	
	/**
	 * The default filename of the address cache
	 * This stores recently found values from the DHT
	 */
	public static final String 	ADDRESSCACHE_FILENAME 	= "whanausip_addresscache.bin";
	
	/**
	 * The default filename of the host cache
	 * This stores the most recent host:port of each buddy
	 * Vital for bootstrapping when a user reconnects
	 */
	public static final String 	HOSTCACHE_FILENAME 		= "whanausip_hostcache.bin";
	
	/**
	 * Number of parallel threads to concurrently run lookupTry
	 */
	public static final int 	NUM_LOOKUP_THREADS		= 100;
	
	/**
	 * Minimum time between requests in WhanauPeer to get remote references
	 * (milliseconds)
	 * Currently set to 10 minute
	 */
	public static final long 	CONTACT_REFRESH_RATE 	= 600000;
	
}
