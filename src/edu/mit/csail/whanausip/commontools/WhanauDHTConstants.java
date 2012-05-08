package edu.mit.csail.whanausip.commontools;

import java.security.Provider;
import java.util.logging.Level;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Store constants for the Whanau DHT protocol
 * 
 * @author ryscheng
 * @date 20100428
 */
public class WhanauDHTConstants {
	/*********************
	 * WHANAUDHT CONSTANTS
	 *********************/
	/**
	 * Local node counts steps*numNodes for each peer's remote call
	 * to sampleNodes(...) per setup
	 * Cannot exceed this number.
	 * Used to limit DoS attacks
	 */
	public static final int 	MAX_RANDWALK_NUMNODESxSTEPS	= Integer.MAX_VALUE;
	/**
	 * Default level minimum level of log entries stored to file
	 */
	public static final Level 	DEFAULT_LOG_LEVEL 			= Level.ALL;
	/**
	 * Maximum number of times to retry sampleNodes(...)
	 * sampleNodes will fail unless all of the requested number
	 * of random walks are returned 
	 */
	public static final int 	MAX_SAMPLENODE_FAILURES 	= 30;
	/**
	 * Default number of randomwalks to cache.
	 * This number is changed when setup() is called to:
	 * [rd + l*(rf + rs)]
	 */
	public static final int 	DEFAULT_RANDWALK_CACHE_SIZE = 100;
	/**
	 * Number of closest successors to return in each call
	 * to successorsSample(...)
	 * Usually 1 or 2
	 */
	public static final int 	SUCCESSORS_SAMPLE_SIZE 		= 2;
	/**
	 * The default username in SIP URI's in the Planet-lab nodes
	 * Not that important
	 */
	public static final String 	DEFAULT_SIP_USERNAME 		= "whanausip";
	/**
	 * Default password for the KeyStore.
	 * Used in Planet-lab nodes, but should be avoided in operation
	 */
	public static final String 	DEFAULT_PASSWORD 			= "password";
	/**
	 * The number of layers in WhanauDHT
	 * Usually between 5 and 10
	 */
	public static final int 	NUMLAYERS 					= 5;
	/**
	 * The number of steps in each random walk
	 * Scales log(n)
	 * Usually between 10-20
	 */
	public static final int 	W 							= 7;
	/**
	 * Parameter values for routing table size in WhanauDHT
	 * See paper for more details on how to set these
	 */
	public static final int 	RD 							= 50;
	public static final int 	RF 							= 50;
	public static final int 	RS 							= 50;
	/**
	 * Default time-to-live for each DHT record
	 * (milliseconds)
	 * currently set to ~ 1 month
	 */
	public static final long 	DEFAULT_TTL 				= Integer.MAX_VALUE;
	
	/*******************************
	 * TIMEOUT VALUES (MILLISECONDS)
	 *******************************/
	/**
	 * The max amount of time to get 3 remote references
	 * Take into account the time to do 3 SSL handshakes
	 */
	public static final int GETREMOTE_TIMEOUT 			= 20000;
	
	/**
	 * The max amount of time to get 3 remote references
	 * Then call a method that takes a short amount of time
	 * This method should have no blocking
	 * ie. getLogStr, getStateStr, getPubKeyHash, etc
	 */
	public static final int SMALLCALL_TIMEOUT 			= 30000;
	
	/**
	 * The amount of time to finish setup().
	 * This is approximately equal to
	 * (randwalk_perstep_time*w) + numlayers*(getid_timeout + successorssample_timeout)
	 */
	public static final int JOINSETUPTHREAD_TIMEOUT 	= 285000;
	
	/**
	 * Max time to wait per step in a random walk
	 * The total allowed time for a random walk is
	 * RANDWALK_PERSTEP_TIMEOUT * W
	 */
	public static final int RANDWALK_PERSTEP_TIMEOUT 	= 17000;
	
	/**
	 * Time for each step to allocate to the local node
	 */
	public static final int RANDWALK_PERSTEP_PROCTIME	= 3000;
	
	/**
	 * Time it takes to get an ID at a layer
	 * getID(layer) does block, so this is the longest we'll wait
	 */
	public static final int GETID_TIMEOUT 				= 50000;
	
	/**
	 * Time it takes to get successors to a key
	 * CPU-intensive method, also blocks
	 */
	public static final int SUCCESSORSSAMPLE_TIMEOUT 	= 60000;
	
	/**
	 * Maximum amount of time we'll allow for a call to query
	 * LOOKUP_TIMEOUT > QUERY_TIMEOUT
	 */
	public static final int QUERY_TIMEOUT 				= 4000;
	
	/**
	 * Maximum amount of time we'll allow for a DHT lookup
	 * This should be short, largely affects call setup latency
	 * LOOKUP_TIMEOUT > QUERY_TIMEOUT
	 */
	public static final int LOOKUP_TIMEOUT 				= 5000;
	
	/**
	 * Max time to wait at start of setup() before performing
	 * random walks to account for misaligned setup starts.
	 */
	public static final int WAITSETUP_TIMEOUT			= 30000;
	
	/**********************
	 * CRYPTOTOOL CONSTANTS
	 **********************/
	public static final Provider CRYPTO_PROVIDER 			= new BouncyCastleProvider();
	public static final String 	CRYPTO_KEY_ALG 				= "RSA";
	public static final int 	CRYPTO_KEY_SIZE 			= 1024;
	public static final String 	CRYPTO_KEYSTORE_TYPE		= "JKS";
	public static final String 	CRYPTO_SIG_ALG 				= "MD5WithRSA";
	public static final String 	CRYPTO_SERIAL_NUM 			= "9009";
	public static final String 	CRYPTO_CERT_NAME 			= "CN=Test V3 Certificate";
	public static final String 	CRYPTO_ALIAS 				= "whanausip";
	public static final String 	KEY_MGMT_ALG				= "SunX509";
	public static final String 	SECURESOCKETPROTOCOL		= "TLS";
	
	/**********************************************************
	 * WHANAUCOMMANDCLIENTLIST COMMANDS (PLANETLAB TESTING)
	 **********************************************************/
	//CONTROL
	public static final String 	CREATENODE_CMD 				= "createNode";
	public static final String 	GETSTATE_CMD 				= "getState";
	public static final String 	GETSTATESTR_CMD 			= "getStateStr";
	public static final String 	GETLOGSTR_CMD 				= "getLogStr";
	public static final String 	ADDPEER_CMD 				= "addPeer";
	public static final String 	REMOVEALLPEERS_CMD 			= "removeAllPeers";
	public static final String 	SETSETUPSTAGE_CMD 			= "setSetupStage";
	public static final String 	RUNSETUPPART1THREAD_CMD		= "runSetupPart1Thread";
	public static final String 	RUNSETUPPART2THREAD_CMD		= "runSetupPart2Thread";
	public static final String 	JOINSETUPTHREAD_CMD 		= "joinSetupThread";
	public static final String 	GETSETUPTHREADRESULT_CMD 	= "getSetupThreadResult";
	public static final String 	LOOKUP_CMD 					= "lookup";
	public static final String 	PUBLISHVALUE_CMD			= "publishValue";
	//PEER
	public static final String 	SAMPLENODES_CMD				= "sampleNodes";
	//PUBLIC
	public static final String 	GETID_CMD					= "getID";
	public static final String 	SUCCESSORSSAMPLE_CMD		= "successorsSample";
	public static final String 	LOOKUPTRY_CMD				= "lookupTry";
	public static final String 	GETPUBKEYHASH_CMD			= "getPubKeyHash";
	public static final String 	WAITSTAGE_CMD 				= "waitStage";
	public static final String 	QUERY_CMD 					= "query";
	
	
	
	
}
