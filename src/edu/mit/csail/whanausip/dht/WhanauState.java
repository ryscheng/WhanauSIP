package edu.mit.csail.whanausip.dht;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import javax.net.ssl.SSLContext;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.threads.*;
import edu.mit.csail.whanausip.dht.kvchecker.*;
import edu.mit.csail.whanausip.sip.SIPMessageProcessor;

/**
 * Stores all WhanauDHT state for a single instance
 * of the protocol (fingers, database, ids, (k,v) etc)
 * Also keeps track of all peers on the social network
 * 
 * This state object is protected by a lock (synchronized),
 * because different RMI threads may access it.
 * Ideally this will become serializable to be stored to file
 * 
 * @author ryscheng
 * @param <T>
 * @date 2009/03/01
 */
public class WhanauState<T> implements SIPMessageProcessor {
	//State of the Whanau node. Stored in setupState
	public static final int STAGE0 = 0; //Node has key and value
	public static final int STAGE1 = 1;	//Node established all peer links
	public static final int STAGE2 = 2;	//Node finished stage 1 of setup(). Has database(u)
	public static final int STAGE3 = 3;	//Node has id(layer=0)
	public static final int STAGE4 = 4;	//Node has fingers(layer=0) and succ(layer=0)
	public static final int STAGE5 = 5; //Node has id(layer=1)
	public static final int STAGE6 = 6;	//Node has fingers(layer=1) and succ(layer=1)
										//Etc etc. last stage = numLayers*2 + 2
	//Local Node Variables
	private Hashtable<String,WhanauRPCClientStub<T>>	
								peers;			//All peers (key=name)
	private Hashtable<String,Integer>
								peerRandWalkCount;//Counts numNodes*steps in calls to sampleNodes(..)
	private Random 				randomGen;		//Random Number generator
	private String 				localHostname;	//Local rmiregistry host
	private int 				localPort;		//local rmiregistry port
	private WhanauKeyState 		keyState;		//Stores the keys for this node
	private RandomWalkCache<T> 	randWalks;		//Store cache of random walks
	private Logger 				log;			//Logs activity
	private String				logFile;		//File path to log file
	//Whanau Variables
	private int 				setupNumber;	//Indicates which setup() we're on
	private int 				setupStage;		//Completed stage in setup()
	private Hashtable <Comparable<T>, Serializable> 
								myValues;		//Published values at this node
	private Hashtable<Comparable<T>, Object>
								myRecords;		//Prepared DHT records containing our values
	private KeyValueChecker<T> 	kvChecker;		//Performs all functions with DHT records (ie. verify)
	private HashSet<Long> 		queryTokens1;	//Set of valid queryTokens for public remote methods
	private HashSet<Long> 		queryTokens2;	//Set of valid queryTokens for public remote methods
	private Hashtable<Comparable<T>, Object>
								database;		//Database of random DHT records
	private ArrayList<Hashtable<Comparable<T>,WhanauRPCClientStub<T>>> 
								fingers;		//A list of fingers per layer 
												//Hashtable (key=id(layer) of finger, value=remote reference)
	private Comparable<T>[] 	ids;			//ID's per layer (WhanauDHT)
	private ArrayList<Hashtable<Comparable<T>, Object>>
								succ;			//Successor list per layer (stored as [k,v])
	
	/**
	 * Creates a new set of WhanauDHT node state
	 * 
	 * @param value 	Serializable 		= value to publish
	 * @param kvChecker	KeyValueChecker<T> 	= Key/Value Checker to use
	 * @param host		String				= local hostname
	 * @param port		int					= local port of public reference
	 * @param keyState	WhanauKeyState		= key state
	 * @param log		Logger				= log
	 * @param logFile	String				= filepath to log file
	 * @throws Exception = fails to create state
	 */
	public WhanauState(Serializable value, KeyValueChecker<T> kvChecker, String host, 
						int port, WhanauKeyState keyState, Logger log, String logFile)	
												throws Exception {
		this.initialize(value,kvChecker,host,port,keyState,log, logFile);
	}
	
	/**
	 * Just create new instances of everything
	 * Currently no method of saving state and
	 * constructing an object from a saved state
	 * Only called by constructor (locked)
	 * 
	 * @param value 	Serializable 		= value to publish
	 * @param kvChecker	KeyValueChecker<T> 	= Key/Value Checker to use
	 * @param host		String				= local hostname
	 * @param port		int					= local port of public reference
	 * @param keyState	WhanauKeyState		= key state
	 * @param log		Logger				= log
	 * @param logFile	String				= filepath to log file
	 * @throws Exception = fails to create state
	 */
	private synchronized void initialize(Serializable value, KeyValueChecker<T> kvChecker, 
								String host, int port, WhanauKeyState keyState, Logger log, String logFile) 
																		throws Exception {
		//These properties were required for RMI; now no more
		/**
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
		System.setProperty("java.rmi.server.hostname", host);
		**/
		this.setSetupStage(0);
		this.setupNumber = 0;
		//Local node
		this.localHostname = host;
		this.localPort = port;
		this.peers = new Hashtable<String,WhanauRPCClientStub<T>>();
		this.peerRandWalkCount = new Hashtable<String, Integer>();
		this.randomGen = new Random();
		this.log = log;
		this.logFile = logFile;
		//Whanau DHT
		this.kvChecker = kvChecker;
		this.database = new Hashtable<Comparable<T>, Object>();
		this.myValues = new Hashtable<Comparable<T>, Serializable>();
		this.myRecords = new Hashtable<Comparable<T>, Object>();
		this.addMyValue(value);
		this.queryTokens1 = new HashSet<Long>();
		this.queryTokens2 = new HashSet<Long>();
		this.fingers = new ArrayList<Hashtable<Comparable<T>, WhanauRPCClientStub<T>>>(this.getNumLayers());
		for (int i=0;i<this.getNumLayers();i++) {
			this.fingers.add(null);
		}
		this.ids = (Comparable<T>[]) new Comparable[this.getNumLayers()];
		this.clearIds();
		this.succ = new ArrayList<Hashtable<Comparable<T>,Object>>(this.getNumLayers());
		for (int i=0;i<this.getNumLayers();i++) {
			this.succ.add(null);
		}
		this.randWalks = new RandomWalkCache<T>(this, WhanauDHTConstants.DEFAULT_RANDWALK_CACHE_SIZE);
		//Crypto
		this.keyState = keyState;
		
		return;
	}
	
	/**
	 * Returns the log for this node
	 *  
	 * @return Logger = log
	 */
	public Logger getLog() {
		return this.log;
	}

	/**
	 * Part of SIPMessageProcessor
	 * Stores an incoming SIP message to the log
	 * 
	 * @param sender 	String = sender address
	 * @param message 	String = incoming message contents
	 */
	public void processMessage(String sender, String message) {
		this.getLog().info("MESSAGE: From="+sender+ "--> Message= "+message);
	}
	
	/**
	 * Part of SIPMessageProcessor
	 * Logs an error on the SIP stack
	 * 
	 * @param errorMessage String = error message
	 */
    public void processError(String errorMessage) {
    	this.getLog().severe(errorMessage);
    }
    
    /**
	 * Part of SIPMessageProcessor
	 * Logs an info message on the SIP stack
	 * 
	 * @param infoMessage String = message
	 */
    public void processInfo(String infoMessage) {
    	this.getLog().info(infoMessage);
    }

    /**
     * Reads the log file and returns it in a single String
     * Requires that the user input a logfile path to monitor
     * 
     * @return String = log file contents
     */
	public synchronized String readLogFile() {
		if (this.logFile == null) { 
			return null;
		}
		StringBuilder contents = new StringBuilder();
	    try {
	    	BufferedReader input =  new BufferedReader(new FileReader(this.logFile));
	    	try {
	    		String line = null;
	    		while (( line = input.readLine()) != null){
	    			contents.append(line);
	    			contents.append(System.getProperty("line.separator"));
	    		}
	    	} finally {
	    		input.close();
	    	}
	    } catch (IOException ex){
	    	this.getLog().severe("Error reading file="+this.logFile+" : "+ex.getMessage());
	    }
	    return contents.toString();
	}
	
	/**
	 * Returns the public key hash
	 * 
	 * @return String
	 */
	public synchronized String getPubKeyHash() {
		return this.keyState.getPubKeyHash();
	}
	
	/**
	 * Returns the local node's hostname
	 * 
	 * @return String
	 */
	public synchronized String getLocalHostname() {
		return localHostname;
	}
	
	/**
	 * Sets the local node's hostname
	 * 
	 * @param host String = new hostname
	 */
	public synchronized void setLocalHostname(String host) {
		this.localHostname = host;
	}
	
	/**
	 * Returns the local node's Whanau DHT port
	 * 
	 * @return int
	 */
	public synchronized int getLocalPort() {
		return localPort;
	}
	
	/**
	 * Returns the SSL context, loaded with the
	 * node's keys
	 * 
	 * @return SSLContex
	 */
	public synchronized SSLContext getSSLContext(){
		return keyState.getSslCtx();
	}
	
	/**
	 * Returns the public key hash
	 * 
	 * @return String
	 */
	public synchronized String getHashHostPort(){
		return (this.getPubKeyHash()+":"+this.getLocalHostname()+":"+this.getLocalPort());
	}
	
	/**
	 * Returns a String representation of this node's state
	 * 
	 * @return String
	 */
	public synchronized String getStateStr(){
		
		Comparable<T> id;
		String result="-----State "+this.getLocalHostname()+":"+this.getLocalPort()+":"+this.getPubKeyHash()+"-----\n";
		result+="My Records:\n";
		for (Comparable<T> key:this.myValues.keySet()) {
			result+="\t "+this.getKVChecker().keyToString(key)+":"+this.myValues.get(key).toString()+"\n";
		}
		result+="setupNumber="+this.getSetupNumber()+"\n";
		result+="setupStage="+this.getSetupStage()+"\n";
		result+="\t Peers\n";
		result+="\t \t <host:port:pubKeyHash> - #randWalkCount \n";
		for (String key:this.peers.keySet()) {
			WhanauRPCClientStub<T> p = this.peers.get(key);
			result+="\t \t "+p.getHashHostPort()+" - ";
			if (this.peers.get(key).isActive()) result+="active";
			else result+="inactive";
			result += " - "+this.peerRandWalkCount.get(key);
			result += "\n";
		}
		result+=this.randWalks.toString();
		result+="Database\n";
		result+="\t <key>==<value>\n";
		for (Comparable<T> key: this.getDatabase().keySet()) {
			result+="\t "+this.getKVChecker().keyToString(key)+"=="+this.getKVChecker().valueToString(this.databaseGet(key))+"\n";
		}
		for (int j=0; j<this.getNumLayers();j++){
			id = this.idGet(j);
			if (id != null) {
				//Fingers
				result+="ID"+j+"="+this.getKVChecker().keyToString(id)+"\n";
				result+="\t Fingers to ID"+j+" \n";
				Set<Comparable<T>> fingerKeys = this.fingerKeys(j);
				if (fingerKeys != null) {
					for (Comparable<T> key:fingerKeys) {
						result+="\t "+this.getKVChecker().keyToString(key)+"\n";
					}
				} else {
					result+="\t\t NULL Finger Table \n";
				}
				result += "\t - \n";
				//Successors
				result+="\t Successors to ID"+j+"\n";
				result+="\t <key>:<value>\n";
				Hashtable<Comparable<T>,Object> succTable = this.succGet(j);
				if (succTable != null) {
					for (Comparable<T> key:succTable.keySet()) {
						result+="\t "+this.getKVChecker().keyToString(key)+"="+this.getKVChecker().valueToString(succTable.get(key))+"\n";
					}
				} else {
					result+="\t\t NULL Successor Table \n";
				}
			} else {
				result+="\t Successors to ID"+j+"= NULL \n";
			}
		}
		result+="---------------------------------\n";
		return result;
	}
	
	/**
	 * Returns the number of layers in this DHT
	 * Just returns a constant due to legacy support
	 * 
	 * @return int = number of layers
	 */
	public synchronized int getNumLayers(){
		return WhanauDHTConstants.NUMLAYERS;
	}
	
	/**
	 * Returns a random integer between [0, i-1]
	 * 
	 * @param i 	= ceiling for random number
	 * @return int 	= random number
	 */
	public synchronized int nextRandInt(int i){
		return this.randomGen.nextInt(i);
	}
	
	/**
	 * Returns a random Long anywhere in the range of a Long
	 * 
	 * @return Long = random numberS
	 */
	public synchronized Long nextRandLong(){
		return this.randomGen.nextLong();
	}
	
	/********************************************
	 *************** QUERY TOKENS ***************
	*********************************************/
	/**
	 * Generate a new query token (random long)
	 * Make sure that it hasn't already been generated
	 * 
	 * @return long = new query token
	 */
	public synchronized long generateQueryToken() {
		long result = this.nextRandLong();
		while (this.queryTokens1.contains(result)) {
			result = this.nextRandLong();
		}
		this.queryTokens1.add(result);
		return result;
	}
	
	/**
	 * Consumes the given query token.
	 * If it was valid, return true
	 * otherwise return false
	 * 
	 * @param tok 	long 	= query token
	 * @return 		boolean = true if valid token, false otherwise
	 */
	public synchronized boolean collectQueryToken(long tok) {
		if (this.queryTokens1.contains(tok))
			return this.queryTokens1.remove(tok);
		else
			return this.queryTokens2.remove(tok);
	}
	
	/**
	 * Remove all query tokens
	 */
	public synchronized void clearQueryTokens() {
		this.queryTokens2 = this.queryTokens1;
		this.queryTokens1 = new HashSet<Long>();
	}

	/******************************************
	 *************** MY RECORDS ***************
	 ******************************************/
	/**
	 * Adds the value as a published value in the DHT
	 * 
	 * @param value Serializable = new value to publish
	 */
	public synchronized void addMyValue(Serializable value) {
		Object record = this.getKVChecker().createRecord(value, this.getLocalHostname(), this.getLocalPort());
		this.myValues.put(this.getKVChecker().getKeyFromRecord(record), value);
		this.myRecords.put(this.getKVChecker().getKeyFromRecord(record), record);
	}
	
	/**
	 * Returns a random publishable record from this node
	 * 
	 * @return Object = random record 
	 */
	public synchronized Object getRandomMyRecord() {
		Comparable<T>[] keys = this.myRecords.keySet().toArray((Comparable<T>[]) new Comparable[0]);
		if (keys.length<=0) {
			this.getLog().warning("No stored key/value pairs");
			return null;
		}
		Comparable<T> k = keys[randomGen.nextInt(keys.length)];
		return this.myRecords.get(k);
	}
	
	/**
	 * Resigns all the values at this node (to refresh TTL) and 
	 * moves them to the record store
	 */
	public synchronized void resignValues() {
		this.myRecords = new Hashtable<Comparable<T>, Object>();
		for (Comparable<T> key : this.myValues.keySet()) {
			this.myRecords.put(key, this.getKVChecker().createRecord(this.myValues.get(key), this.getLocalHostname(), this.getLocalPort()));
		}
	}
	
	/***************************************
	 *************** PEERS *****************
	 ***************************************/
	/**
	 * Adds a peer to the PeerList
	 * Resets its random walk count
	 * 
	 * @param p WhanauRPCClientStub<T> 	= new peer
	 * @return WhanauRPCClientStub<T>	= previous value associated with
	 * 									the key (p.getIdentifier())
	 */
	public synchronized WhanauRPCClientStub<T> addPeer(WhanauRPCClientStub<T> p) {
		WhanauRPCClientStub<T> result = this.peers.put(p.getPubKeyHash(), p);
		this.peerRandWalkCount.put(p.getPubKeyHash(), 0);
		this.getLog().finest("peer="+p.getHashHostPort());
		return result;
	}
	
	/**
	 * Removes all peers and resets the random walk counters
	 */
	public synchronized void removeAllPeers() {
		this.peers = new Hashtable<String,WhanauRPCClientStub<T>>();
		this.peerRandWalkCount = new Hashtable<String, Integer>();
		this.getLog().fine("Done");
	}
	
	/**
	 * Removes the peer from the PeerList
	 * 
	 * @param p WhanauRPCClientStub<T> 	= peer to remove
	 * @return WhanauRPCClientStub<T> 	= peer removed. null if peer does not exist
	 */
	public synchronized WhanauRPCClientStub<T> removePeer(WhanauRPCClientStub<T> p) {
		WhanauRPCClientStub<T> result = this.peers.remove(p.getPubKeyHash());
		this.peerRandWalkCount.remove(p.getPubKeyHash());
		this.getLog().finest("peer="+p.getHashHostPort());
		return result;
	}

	/**
	 * Get the peer with the given public key hash
	 * 
	 * @param hash String 				= peer's public key hash
	 * @return WhanauRPCClientStub<T> 	= peer's reference. null if does not exist
	 */
	public synchronized WhanauRPCClientStub<T> getPeerByPubKeyHash(String hash) {
		return peers.get(hash);
	}
	
	/**
	 * Returns all of the keys for all peers
	 * 
	 * @return Set<String>
	 */
	public synchronized Set<String> getAllPeerKeys() {
		return this.peers.keySet();
	}
	
	/**
	 * Returns a set of all the active peers.
	 * Actively pings them to check alive in a separate thread
	 * 
	 * @return Set<WhanauRPCClientStub<T>> = active peer references
	 */
	public synchronized Set<WhanauRPCClientStub<T>> getActivePeers(){
		Set<String> keySet = this.getAllPeerKeys();
		HashSet<WhanauRPCClientStub<T>> activePeerSet = new HashSet<WhanauRPCClientStub<T>>();
		
		WhanauRPCClientStub<T> currPeer;
		for (String key:keySet) {
			currPeer = this.getPeerByPubKeyHash(key);
			if (currPeer != null && (currPeer.isActive()))
				activePeerSet.add(currPeer);
		}
		return activePeerSet;
	}
	
	/**
	 * Resets all peer's active status to true
	 */
	public synchronized void resetPeerActiveStatus() {
		Set<String> keys = this.getAllPeerKeys();
		for (String key : keys) {
			this.getPeerByPubKeyHash(key).setActiveStatus(true);
		}
	}
	
	/**
	 * Returns the random walk count for a peer
	 * random walk count = sum(numNodes*steps for calls to sampleNodes)
	 * 
	 * @param pubKeyHash String = pubKeyHash of peer
	 * @return int 				= random walk count
	 */
	public synchronized int getPeerRandWalkCount(String pubKeyHash) {
		return this.peerRandWalkCount.get(pubKeyHash);
	}
	
	/**
	 * Resets all random walk counts without removing peers
	 */
	public synchronized void resetPeerRandWalkCount(){
		for (String key : this.peerRandWalkCount.keySet()) {
			this.peerRandWalkCount.put(key, 0);
		}
	}
	
	/**
	 * Increments the random walk count for a peer
	 * Does nothing if peer doesn't exist
	 * 
	 * @param pubKeyHash String = pubKeyHash of peer
	 * @param toAdd		 int	= count to add
	 */
	public synchronized void addPeerRandWalkCount(String pubKeyHash, int toAdd) {
		if (this.peerRandWalkCount.containsKey(pubKeyHash)) {
			int newValue = this.peerRandWalkCount.get(pubKeyHash)+toAdd;
			this.peerRandWalkCount.put(pubKeyHash, newValue);
		}
	}
	
	/**
	 * Reset the random walk cache 
	 * and set it to a specified depth
	 * 
	 * @param depth int = new depth 
	 */
	public synchronized void resetRandWalks(int depth){
		this.getLog().fine("depth="+depth);
		this.randWalks.reset(depth);
	}
	
	/*******************************************
	 *************** SETUP STAGE ***************
	 *******************************************/
	/**
	 * Returns the current setup stage
	 * 
	 * @return int = setup stage
	 */
	public synchronized int getSetupStage() {
		return setupStage;
	}
	
	/**
	 * Waits for the setupState of this to be at least desiredStage.
	 * If not, the thread will wait until.
	 * 
	 * @param desiredStage int = The setupStage should be at least as large as this
	 * @throws InterruptedException = if another thread interrupts this while waiting
	 */
	public synchronized void waitForSetupStage(int desiredStage) throws InterruptedException {
		while (this.getSetupStage() < desiredStage)	{
			this.wait();
		}
	}
	
	/**
	 * Indicate that the whanu protocol has reached the next setup state.
	 * Notify all sleeping threads
	 * 
	 * @param setupState int = new state of setup
	 */
	public synchronized void setSetupStage(int setupStage) {
		this.setupStage = setupStage;
		this.notifyAll();
	}
	
	/**
	 * Returns the setup number.
	 * This is a unique number to identify which iteration of setup()
	 * 
	 * @return int
	 */
	public synchronized int getSetupNumber() {
		return setupNumber;
	}
	
	/**
	 * Increment the setup number.
	 * Called at the beginning of setup
	 * 
	 * @return int
	 */
	public synchronized int incSetupNumber() {
		return ++this.setupNumber;
	}
	
	/****************************************
	 *************** DATABASE ***************
	 ****************************************/
	
	/**
	 * Just a shell function for put into database
	 * 
	 * @param key 	Comparable 	= key
	 * @param value Object 		= value
	 * @return 		Object 		= result from database.put
	 */
	public synchronized Object databasePut(Comparable<T> key, Object value){
		return this.database.put(key,value);
	}

	/**
	 * Just a shell function for get into database
	 * 
	 * @param key Comparable = key
	 * @return Object = result from database.get
	 */
	public synchronized Object databaseGet(Comparable<T> key){
		return this.database.get(key);
	}
	
	/**
	 * Returns entire database
	 * 
	 * @return Hashtable<Comparable<T>,Object>
	 */
	public synchronized Hashtable<Comparable<T>,Object> getDatabase() {
		return this.database;
	}
	
	/**
	 * Return a random key from database
	 * 
	 * @return Comparable = random key
	 */
	public synchronized Comparable<T> getRandomKeyInDatabase() {
		Set<Comparable<T>> keySet = this.database.keySet();
		Comparable<T>[] keys = keySet.toArray((Comparable<T>[]) new Comparable[0]);
		if (keys.length<=0) {
			this.getLog().warning("No entries in database");
			return null;
		}
		return keys[randomGen.nextInt(keys.length)];
	}
	
	/**
	 * Clears out the database before we get a new one
	 */
	public synchronized void clearDatabase() {
		this.database = new Hashtable<Comparable<T>, Object>();
	}
	
	/***************************************
	 *************** FINGERS ***************
	 ***************************************/
	/**
	 * Just a shell function for put into finger hashtable
	 * 
	 * @param layer	int						= layer to place finger
	 * @param id_i 	Comparable<T> 			= id(i) for this finger
	 * @param n 	WhanauRPCClientStub<T> 	= new finger
	 * @return WhanauRPCClientStub<T> 		= result from fingers.put(id0,n)
	 */
	public synchronized WhanauRPCClientStub<T> fingerPut(int layer, Comparable<T> id_i, WhanauRPCClientStub<T> n){
		Hashtable<Comparable<T>, WhanauRPCClientStub<T>> currTable = this.fingers.get(layer);
		if (currTable == null) {
			currTable = new Hashtable<Comparable<T>, WhanauRPCClientStub<T>>();
			this.fingers.set(layer, currTable);
		}
		if ((id_i != null) && (n != null)) { 
			return currTable.put(id_i,n);
		} else {
			return null;
		}
	}
	
	/**
	 * Resets the finger table at a layer
	 * 
	 * @param layer int = layer to reset finger table
	 */
	public synchronized void clearFingers(int layer) {
		this.fingers.set(layer, new Hashtable<Comparable<T>, WhanauRPCClientStub<T>>());
	}
	
	/**
	 * Shell function get in finger hashtable
	 * Returns id(i) for this finger
	 * 
	 * @param layer int					= layer from which to retrieve finger
	 * @param key 	Comparable<T>	 	= finger id(i)
	 * @return WhanauRPCClientStub<T> 	= remote reference to this finger
	 */
	public synchronized WhanauRPCClientStub<T> fingerGet(int layer, Comparable<T> key) {
		Hashtable<Comparable<T>, WhanauRPCClientStub<T>> currTable = this.fingers.get(layer);
		if (currTable == null) {
			this.getLog().warning("No finger table at layer="+layer);
			return null;
		} 
		return currTable.get(key);
	}
	
	/**
	 * Shell function for set of keys (id(i)) in fingers hashtable
	 * 
	 * @param layer int				= layer to retrieve finger keys from
	 * @return Set<Comparable<T>> 	= id(i) for all fingers
	 */
	public synchronized Set<Comparable<T>> fingerKeys(int layer) {
		Hashtable<Comparable<T>, WhanauRPCClientStub<T>> currTable = this.fingers.get(layer);
		if (currTable == null) {
			this.getLog().warning("No finger table at layer="+layer);
			return null;
		}	
		return currTable.keySet();
	}
	
	/**
	 * Returns a random finger's id_i in a layer
	 * @param layer int 	 = layer to get finger from
	 * @return Comparable<T> = finger's id_i 
	 */
	public synchronized Comparable<T> getRandomFingerId(int layer) {
		Set<Comparable<T>> fingerSet = this.fingerKeys(layer);
		if (fingerSet == null)
			return null;
		Comparable<T>[] array = fingerSet.toArray(new Comparable[0]);
		if (array.length<=0) {
			this.getLog().warning("No finger table at layer="+layer);
			return null;
		}
		return array[randomGen.nextInt(array.length)];
	}
	
	/***************************************
	 *************** ID'S ******************
	 ***************************************/
	/**
	 * Gets an id from its id array. 
	 * 
	 * @param i int = get id from layer i
	 * @return Object = id, null if out of bounds
	 */
	public synchronized Comparable<T> idGet(int i) {
		if ((i>=0)&&(i<this.getNumLayers())){
			return this.ids[i];
		} else {
			this.getLog().warning("Layer out of bounds, requested layer="+i);
			return null;
		}
	}
	
	/**
	 * Clears out all ID's with null
	 */
	public synchronized void clearIds(){
		for (int i = 0; i < this.ids.length; i++){
			this.ids[i] = null;
		}
	}
	
	/**
	 * Puts an id into its id array. Does nothing if i out of bounds.
	 * 
	 * @param i  int 	= put into layer i
	 * @param id Object = id to put into array
	 */
	public synchronized void idPut(int i, Comparable<T> id){
		if ((i>=0)&&(i<this.getNumLayers())){
			this.ids[i] = id;
		} else {
			this.getLog().warning("Layer out of bounds, requested layer="+i);
		}
	}
	
	/***************************************
	 *************** SUCCESSORS ************
	 ***************************************/
	/**
	 * Gets an successor list from its succ array. 
	 * 
	 * @param i int		 = get succ list from layer i
	 * @return Hashtable = successor list, null if out of bounds
	 */
	public synchronized Hashtable<Comparable<T>,Object> succGet(int i){
		if ((i>=0)&&(i<this.getNumLayers())){
			return this.succ.get(i);
		}
		else {
			this.getLog().warning("Layer out of bounds, requested layer="+i);
			return null;
		}
	}
	/**
	 * Puts a successor list into its succ array. Does nothing if i out of bounds.
	 * 
	 * @param i 	int 							= put into layer i
	 * @param id 	Hashtable<Comparable, Object> 	= successor list to put into array
	 */
	public synchronized void succPut(int i, Hashtable<Comparable<T>,Object> newList){
		if ((i>=0)&&(i<this.getNumLayers())){
			this.succ.set(i, newList);
		} else {
			this.getLog().warning("Layer out of bounds, requested layer="+i);
		}
	}
	

	/**************************
	 ******** NOT LOCKED ******
	 **************************/
	
	
	/**
	 * Retrieves random walks from our random walk cache
	 * 
	 * @param numNodes int = number of nodes to retrieve
	 * @param steps	   int = number of steps in random walk
	 * @return LinkedList<Pair<Object, Long>> = list of [queryToken, DHT record]
	 */
	public LinkedList<Pair<Object, Long>> sampleNodes(int numNodes, int steps) {
		LinkedList<Pair<Object, Long>> result = this.randWalks.sampleNodes(numNodes, steps);
		if (result == null) {
			this.log.warning("(numNodes="+numNodes+",steps="+steps+") returned false :-(");
		} else {
			this.log.fine("(numNodes="+numNodes+",steps="+steps+") success!");
		}
		return result;
	}
	
	/**
	 * Returns the key value checker
	 * 
	 * @return KeyValueChecker<T>
	 */
	public KeyValueChecker<T> getKVChecker() {
		return this.kvChecker;
	}
}
