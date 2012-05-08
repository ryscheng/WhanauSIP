package edu.mit.csail.whanausip.dht;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.*;
import edu.mit.csail.whanausip.commontools.threads.*;
import edu.mit.csail.whanausip.dht.kvchecker.KeyValueChecker;

/**
 * This defines all of the methods used to perform
 * administrative control functions.
 * This must be bound in a RMI registry for bootstrapping
 * Only the owners of the public keys in this.controlKeys
 * can retrieve a remote reference to this object and remotely administer
 * 
 * If this.controlkeys is null anyone can administer
 * 
 * @author ryscheng
 * @date 2010/07/20
 */
public class WhanauRefControlImpl<T> //extends UnicastRemoteObject
									implements WhanauRefControl<T>,MethodThreadInterface {
	
	private static final long serialVersionUID = 6434488836159215662L;
	private WhanauState<T> 		state;			//Local node's state
	private MethodThreadRunner 	setupHandler;	//Holds the thread and result for setup()
	private Thread 				setupThread;	//Thread that runs setup()
	private Set<String> 		controlKeys;	//authorized controller keys
	private HashSet<WhanauVirtualNode<T>> childNodes;	 //Key = password, value = node
	
	/**
	 * Create a new control reference, listening on port+2
	 * This remote object needs to be bound to an RMI registry
	 * 
	 * @param state			WhanauState<T>	= local node's state
	 * @param controlKeys	Set<String>		= control keys
	 * @throws RemoteException
	 */
	public WhanauRefControlImpl(WhanauState<T> state, Set<String> controlKeys) throws RemoteException{
		//super(state.getLocalPort()+2, new RMISSLClientSocketFactory(state.getPubKeyHash(), state.getPubKeyHash()), 
		//			new RMISSLServerSocketFactory(state.getPubKeyHash(), controlKeys, state.getLog()));
		
		this.state = state;
		this.controlKeys = controlKeys;
		this.childNodes = new HashSet<WhanauVirtualNode<T>>();
		//state.info("Initializing a new WhanauRefControlImpl on "+state.getHashHostPort());
	}
	
	/**
	 * Returns the children of this node
	 * 
	 * @return Set<WhanauVirtualNode<T>>
	 */
	protected Set<WhanauVirtualNode<T>> getChildNodes(){
		return this.childNodes;
	}
	
	/**
	 * This method contains all operations that need to be parallelized.
	 * Currently only setup and
	 * parallelquery = lookupTry, successorsSample, getID
	 * 
	 * @param param Object[] 	= parameters
	 * 				Object[0]	= command
	 * @return		Object		= return object 
	 */
	public Object methodThread(Object[] param){
		String command = null;
		try {
			command = (String) param[0];
			if (command.equals("setuppart1")) {
				return new Long(this.setuppart1((Integer)param[1], (Integer)param[2], (Integer)param[3], (Integer)param[4]));
			} else if (command.equals("setuppart2")) {
				return new Long(this.setuppart2((Integer)param[1], (Integer)param[2], (Integer)param[3], (Integer)param[4]));
			} else {
				WhanauRPCClientStub<T> n = (WhanauRPCClientStub<T>) param[1];
				return n.remoteCall(command, param[2], param[3], param[4]);
			}
		} catch (Exception ex) {
			this.state.getLog().warning("remote call to "+command+" failed: "+ex.getMessage());
		}
		return null;
	}
	
	/**
	 * Performs the first part of setup() sequence in WhanauDHT
	 * Does everything up to and including getting database
	 * This is usually called in a separate thread that can be monitored
	 * Need rd + l*(rf + rs) random walks per setup
	 * 
	 * @param w		int = number of steps in each random walk
	 * @param rd	int = number of database entries
	 * @param rf	int = number of fingers
	 * @param rs	int = number of successors per layer
	 * @return boolean = time in ms to finish. 0 if failure
	 */
	private long setuppart1(int w, int rd, int rf, int rs) {
		long startTime = System.currentTimeMillis();
		MethodThreadBatchRun batch;
		int currSetupNum = this.state.incSetupNumber();
		this.state.getLog().info("setuppart1(w="+w+",rd="+rd+",rf="+rf+",rs="+rs+") STARTED");
		//Reset our temporary state
		this.state.resignValues();
		this.state.clearQueryTokens();
		this.state.resetRandWalks((rd + this.state.getNumLayers() * (rf + rs)));
		this.state.resetPeerRandWalkCount();
		this.state.resetPeerActiveStatus();
		Runtime.getRuntime().gc();
		this.state.setSetupStage(1);
		
		//Synchronize setups
		Set<WhanauRPCClientStub<T>> activePeers = this.state.getActivePeers();
		batch = new MethodThreadBatchRun();
		int temp=0;
		for (WhanauRPCClientStub<T> n : activePeers) {
			batch.addThread(Integer.toString(temp++), this, WhanauDHTConstants.WAITSTAGE_CMD, n, WhanauState.STAGE1, null,null);
		}
		batch.joinTermination(WhanauDHTConstants.WAITSETUP_TIMEOUT);
		batch.getFinalResults();
		//Stage 2 - Fill Database
		LinkedList<Pair<Object, Long>> sampleNodesResult = this.persistentSampleNodes(currSetupNum, rd, w);
		if (sampleNodesResult == null) {
			this.state.getLog().severe("failed in stage 2: sampleRecords");
			return 0;
		}
		this.state.clearDatabase();
		for (Pair<Object, Long> value: sampleNodesResult) {
			Comparable<T> key = this.state.getKVChecker().getKeyFromRecord(value.getFirst());
			if ((key != null) && (value != null)) this.state.databasePut(key, value.getFirst());
		}
		this.state.setSetupStage(2);
		this.state.getLog().info(this.state.getPubKeyHash()+" finished stage=2: got DB");
		
		long setupTime = System.currentTimeMillis() - startTime;
		this.state.getLog().info("setuppart1("+w+","+rd+","+rf+","+rs+") finished in "+setupTime+"ms for pubKeyHash="+this.state.getPubKeyHash());
		Runtime.getRuntime().gc();
		return setupTime;
	}
	
	/**
	 * Performs the second part of setup() sequence in WhanauDHT
	 * Starts after database, get ID, fingers, and successors
	 * This is usually called in a separate thread that can be monitored
	 * Need rd + l*(rf + rs) random walks per setup
	 * 
	 * @param w		int = number of steps in each random walk
	 * @param rd	int = number of database entries
	 * @param rf	int = number of fingers
	 * @param rs	int = number of successors per layer
	 * @return boolean = time in ms to finish. 0 if failure
	 */
	private long setuppart2(int w, int rd, int rf, int rs) {
		long startTime = System.currentTimeMillis();
		Hashtable<WhanauRPCClientStub<T>, Object> parallelResult;
		MethodThreadBatchRun batch;
		int currSetupNum = this.state.getSetupNumber();
		this.state.getLog().info("setuppart2(w="+w+",rd="+rd+",rf="+rf+",rs="+rs+") STARTED");
		Set<WhanauRPCClientStub<T>> activePeers = this.state.getActivePeers();
		int stage;
		
		Runtime.getRuntime().gc();
		//Check if isolated
		if (activePeers.size() <= 0) {
			this.state.getLog().severe("failed before setup part 2: no active peers");
			return 0;
		} 
		//Synchronize setups
		batch = new MethodThreadBatchRun();
		int temp=0;
		for (WhanauRPCClientStub<T> n : activePeers) {
			batch.addThread(Integer.toString(temp++), this, WhanauDHTConstants.WAITSTAGE_CMD, n, WhanauState.STAGE2, null,null);
		}
		batch.joinTermination(WhanauDHTConstants.WAITSETUP_TIMEOUT);
		batch.getFinalResults();
		
		//Stages 3 - (2*l + 2)
		//Get ID's, fingers, and successors
		stage = 3;
		for (int i=0; i<this.state.getNumLayers(); i++) {
			//CHOOSE-ID
			Comparable<T> id_i = this.chooseId(i);
			if (id_i != null) { 
				this.state.idPut(i, id_i);
			} else {
				this.state.getLog().severe("failed in stage="+stage+": getting ID for layer="+i);
				return 0;
			}
			this.state.setSetupStage(stage);
			this.state.getLog().info(this.state.getPubKeyHash()+" finished stage="+stage+": got ID for layer="+i);
			stage++;
			//FINGERS
			parallelResult = this.parallelQuery(currSetupNum, rf, w, WhanauDHTConstants.GETID_TIMEOUT, "getID", i, null);
			if (parallelResult == null) {
				this.state.getLog().severe("failed in stage "+stage+": get fingers for layer "+i);
				return 0;
			}
			this.state.clearFingers(i);
			for (WhanauRPCClientStub<T> node: parallelResult.keySet()) {
				Comparable<T> key = (Comparable<T>) parallelResult.get(node);
				if (key != null) this.state.fingerPut(i, key, node);
			}
			//SUCCESSORS
			parallelResult = this.parallelQuery(currSetupNum, rf, w, WhanauDHTConstants.SUCCESSORSSAMPLE_TIMEOUT,
													"successorsSample", this.state.idGet(i), null);
			if (parallelResult == null) {
				this.state.getLog().severe("failed in stage "+stage+": get successors for layer "+i);
				return 0;
			}
			Hashtable<Comparable<T>,Object> succTable = new Hashtable<Comparable<T>,Object>();
			for (WhanauRPCClientStub<T> node: parallelResult.keySet()) {
				Object[] currResult = (Object[]) parallelResult.get(node);
				if (currResult != null) {
					for (int j=0;j<currResult.length;j++) {
						if (currResult[j] != null) {
							Comparable<T> key = this.state.getKVChecker().getKeyFromRecord(currResult[j]);
							if (this.state.getKVChecker().checkKeyRecord(key,currResult[j])) {
								succTable.put(key, currResult[j]);
							} else {
								this.state.getLog().warning("encountered a malformed value in successorsSample result");
							}
						}
					}
				} else {
					this.state.getLog().warning("encountered a null result from successorsSample");
				}
			}
			this.state.succPut(i, succTable);
			
			this.state.setSetupStage(stage);
			this.state.getLog().info(this.state.getPubKeyHash()+" finished stage="+stage+
										": Got fingers and successors for layer="+i);
			stage++;
		}
		long setupTime = System.currentTimeMillis() - startTime;
		this.state.getLog().info("setuppart2("+w+","+rd+","+rf+","+rs+") finished in "+setupTime+"ms for pubKeyHash="+this.state.getPubKeyHash());
		Runtime.getRuntime().gc();
		return setupTime;
	}
	
	/**
	 * A small method for choosing the ID at a particular layer.
	 * See WhanauDHT paper for more details
	 * If layer > 0, the finger at (layer-1) must have already
	 * been retrieved
	 * 
	 * @param layer int 			= want the ID at this layer
	 * @return 		Comparable<T> 	= ID at layer
	 */
	private Comparable<T> chooseId(int layer) {
		//If first layer
		if (layer==0) {
			return this.state.getRandomKeyInDatabase();
		} else {
			return this.state.getRandomFingerId(layer-1);
		}
	}
	
	/**
	 * Checks if the setup number is what we expect.
	 * If not, return null
	 * Otherwise, keeps trying to call sampleNodes(...) until we reach our retry limit.
	 * SampleNodes only returns with results if we get all numNodes results
	 * 
	 * @param currSetupNum 	int = expected setup number 
	 * @param numNodes		int	= number of random walks we want
	 * @param steps			int = number of steps in random walk
	 * @return LinkedList<Pair<Object, Long>> = random walk results in a list
	 */
	private LinkedList<Pair<Object, Long>> persistentSampleNodes(int currSetupNum, int numNodes, int steps) {
		LinkedList<Pair<Object, Long>> records = null;
		int i=0;
		
		if (currSetupNum != this.state.getSetupNumber()) {
			this.state.getLog().severe("failed sampleNodes: current setupNumber="+currSetupNum+
										", new setupNumber="+this.state.getSetupNumber());
			return null;
		}
		//Samplenodes must return all numNodes results, otherwise returns null
		//Only try a limited number of times
		//50% to add an extra step to account for certain types of graphs
		//if (this.state.nextRandInt(100) < 50) steps--;
		while ((i < WhanauDHTConstants.MAX_SAMPLENODE_FAILURES) && records == null) {
			records = this.state.sampleNodes(numNodes, steps); 
			i++;
		}
		return records;
	}
	
	/**
	 * Generalizes parallel queries to random walks results.
	 * Gets numNodes of random nodes, and parallelize requests to all of them.
	 * There are 3 currently implemented: 
	 * fingers = parallelQuery(rf, w, "getID", layer)
	 * successors = parallelQuery(rs, w, "successorsSample", id(layer))
	 * lookup = parallelQuery(threads, w, "lookupTry", key)
	 * 
	 * @param numNodes 		int 				= number of random walks to perform
	 * @param steps 		int 				= number of steps in random walk
	 * @param parallelCmd 	String 				= command to perform in methodThread
	 * @param args 			Object 				= argument to parallelCmd (at most 1)
	 * @return 	Hashtable<Comparable, Object> 	= table of (key,value) from random walks
	 */
	private Hashtable<WhanauRPCClientStub<T>, Object> parallelQuery(int currSetupNum, int numNodes, int steps, 
																int timeout, String parallelCmd, Object arg1, Object arg2) {
		this.state.getLog().fine("(currSetupNum="+currSetupNum+",numNodes="+numNodes+",stepsize="+steps+
									",timeout="+timeout+",parallelCmd="+parallelCmd+",arg1="+arg1+",arg2="+arg2+")");
		Hashtable<WhanauRPCClientStub<T>, Object> finalResult = new Hashtable<WhanauRPCClientStub<T>, Object>();
		Hashtable<String, WhanauRPCClientStub<T>> nodeTable = new Hashtable<String,WhanauRPCClientStub<T>>();
		MethodThreadBatchRun batch = new MethodThreadBatchRun();
		LinkedList<Pair<Object, Long>> records = null;
		int i=0;
		
		records = this.persistentSampleNodes(currSetupNum, numNodes, steps);
		//If keeps failing, just give up
		if (records == null) {
			this.state.getLog().severe("cannot sample enough nodes (currSetupNum="+currSetupNum+",numNodes="+numNodes+
									",stepsize="+steps+",timeout="+timeout+",parallelCmd="+parallelCmd+",arg1="+arg1+",arg2="+arg2+")");
			return null;
		}
		//Parallelize get (k,v) from all sampleNodes
		for (Pair<Object, Long> randWalkResult : records) {
			WhanauRPCClientStub<T> n = this.state.getKVChecker().getPtrFromRecord(randWalkResult.getFirst());
			batch.addThread(Integer.toString(i), this, parallelCmd, n, randWalkResult.getSecond(), arg1, arg2);
			nodeTable.put(Integer.toString(i), n);
			i++;
		}
		batch.joinFixedTime(timeout);
		Hashtable<String, Object> batchResult = batch.getFinalResults();
		//Add to the final result
		for (String index:batchResult.keySet()) {
			WhanauRPCClientStub<T> key = nodeTable.get(index);
			Object value = batchResult.get(index);
			if ((key != null)&&(value != null)) finalResult.put(key, value);
		}
		if (finalResult.size() <= 0) {
			this.state.getLog().severe("(currSetupNum="+currSetupNum+",numNodes="+numNodes+",stepsize="+steps+
										",timeout="+timeout+",parallelCmd="+parallelCmd+",arg1="+arg1+
										",arg2="+arg2+") failed: no results to return");
			return null;
		}
		return finalResult;
	}

	/************************************************
	 ************** CONTROL METHODS******************
	 ************************************************/
	/**
	 * Creates a new WhanauDHT virtual node
	 * This is merely a bootstrapping mechanism
	 * In order to control it, you use the returned WhanauRefControl reference
	 * New node is controlled by same keys as this node
	 * 
	 * @param value		Serializable	= value to store at this node
	 * @param kvChecker KeyValueChecker	= key/value checker
	 * @param host		String			= hostname
	 * @param port		int				= port WhanauDHT listens on
	 * @param keys		KeyStore		= keys
	 * @param keyPasswd	String			= password to unlock keys
	 * @return WhanauRefControl 		= remote reference to control new instance
	 * @throws Exception
	 */
	public WhanauRefControl<T> createNode(Serializable value, KeyValueChecker<T> kvChecker,
											String host, int port, KeyStore keys, String keyStorePassword) throws Exception {
		Logger log = LogUtil.createLogger("whanau"+port, null);
		WhanauKeyState keyState = new WhanauKeyState(keys, keyStorePassword);
		WhanauState<T> state = new WhanauState<T>(value, kvChecker, host, port, keyState, log, null);
		WhanauVirtualNode<T> server = new WhanauVirtualNode<T>(state, this.controlKeys);
		this.childNodes.add(server);
		return server.getControlRef();
	}
	
	/**
	 * Returns the state of this node
	 * 
	 * @return WhanauState<T> = state
	 * @throws RemoteException
	 */
	public WhanauState<T> getState() throws RemoteException {
		this.state.getLog().fine("success");
		return this.state;
	}
	
	/**
	 * Returns string representation of the state
	 * 
	 * @return String = string representation
	 * @throws RemoteException
	 */
	public String getStateStr() throws RemoteException {
		this.state.getLog().fine("success");
		return this.state.getStateStr();
	}
	
	/**
	 * Returns a string containing all entries in the log
	 * 
	 * @return String = log
	 * @throws RemoteException
	 */
	public String getLogStr() throws RemoteException {
		this.state.getLog().fine("success");
		return this.state.readLogFile();
	}
	
	/**
	 * Adds a peer to this node in the social graph
	 * 
	 * @param pubKeyHash	String	= peer's SHA1 hash of public key
	 * @param host			String	= peer's hostname
	 * @param port			int		= peer's WhanauDHT port
	 * @return boolean 				= true if peer is added, false if fail
	 * @throws RemoteException
	 */
	public boolean addPeer(String pubKeyHash, String host, int port) throws RemoteException {
		WhanauRPCClientStub<T> p = new WhanauRPCClientStub<T>(this.state.getLog(),this.state.getSSLContext(),
												pubKeyHash,host,port);
		this.state.addPeer(p);
		this.state.getLog().info(this.state.getHashHostPort()+" added "+pubKeyHash+":"+host+":"+port+" as a peer");
		return true;
	}
	
	/**
	 * Removes all peers from the state
	 * 
	 * @return true always
	 * @throws RemoteException
	 */
	public boolean removeAllPeers() throws RemoteException {
		this.state.removeAllPeers();
		this.state.getLog().info("removed all peers");
		return true;
	}
	
	/**
	 * Sets the current setup stage.
	 * Main purpose is to set stage=0 at a synchronized time before setup().
	 * That way nodes block as they should if setup() is called at slightly 
	 * different times.
	 * 
	 * @param stage 	int 	= stage to set it to
	 * @return 			boolean = true always
	 * @throws RemoteException
	 */
	public boolean setSetupStage(int stage) {
		this.state.setSetupStage(stage);
		this.state.getLog().info("setting setup stage to "+stage);
		return true;
	}
	
	/**
	 * Initiates a call to setup oart 1
	 * sample nodes and database
	 * 
	 * @param w		int = number of steps in each random walk
	 * @param rd	int = number of database entries
	 * @param rf	int = number of fingers
	 * @param rs	int = number of successors per layer
	 * @return boolean 	= true if successfully started, false otherwise
	 * @throws RemoteException
	 */
	public boolean runSetupPart1Thread(int w, int rd, int rf, int rs) throws RemoteException{
		this.setupHandler = new MethodThreadRunner("setup:"+this.state.getLocalPort(),this, 
													"setuppart1", w, rd, rf, rs);
		this.setupThread = new Thread(this.setupHandler, this.setupHandler.getID());
		this.setupThread.start();
		this.state.getLog().info("spawning SETUP PART 1 thread");
		return true;
	}
	
	/**
	 * Initiates a call to setup part 2
	 * get ID, fingers, successors
	 * 
	 * @param w		int = number of steps in each random walk
	 * @param rd	int = number of database entries
	 * @param rf	int = number of fingers
	 * @param rs	int = number of successors per layer
	 * @return boolean 	= true if successfully started, false otherwise
	 * @throws RemoteException
	 */
	public boolean runSetupPart2Thread(int w, int rd, int rf, int rs) throws RemoteException{
		this.setupHandler = new MethodThreadRunner("setup:"+this.state.getLocalPort(),this, 
													"setuppart2", w, rd, rf, rs);
		this.setupThread = new Thread(this.setupHandler, this.setupHandler.getID());
		this.setupThread.start();
		this.state.getLog().info("spawning SETUP PART 2 thread");
		return true;
	}
	
	/**
	 * Joins the setup thread. Blocks until setup is done
	 * 
	 * @return boolean = true if successful, false if not
	 * @throws RemoteException
	 * @throws InterruptedException
	 */
	public boolean joinSetupThread() throws RemoteException, InterruptedException{
		if (this.setupThread == null) {
			this.state.getLog().warning("failure! - no setup thread exists");
			return false;
		}
		this.state.getLog().info("joining setup thread");
		this.setupThread.join();
		this.state.getLog().info("thread done");
		return (Boolean) true;
	}
	
	/**
	 * Returns the result of setup()
	 * 
	 * @return long = time to completion in milliseconds, 0 if failure
	 * @throws RemoteException
	 */
	public long getSetupThreadResult() throws RemoteException{
		if (this.setupHandler == null) {
			this.state.getLog().warning("failure - no setup thread exists");
			return 0;
		}
		long result = ((Long)this.setupHandler.getResult()).longValue();
		if (result != 0) this.state.getLog().info(result+"ms - returned true");
		else this.state.getLog().info("returned false");
		return result;
	}
	
	/************************************************
	 ************** CLIENT METHODS ******************
	 ************************************************/
	
	/**
	 * Performs a lookup on a given key in the DHT
	 * 
	 * @param lookupTimeout	int				= timeout value for lookupTry()
	 * @param queryTimeout	int				= timeout value for query()
	 * @param numThreads	int 			= number of parallel lookup threads
	 * @param w				int 			= number of steps per random walk
	 * @param key			Comparable<T>	= key to lookup
	 * @return Object 						= DHT record containing the value
	 * @throws RemoteException
	 */
	public Object lookup(int lookupTimeout, int queryTimeout, int numThreads, int w, Comparable<T> key) throws RemoteException {
		this.state.getLog().fine("(numThreads="+numThreads+", w="+w+", key="+this.state.getKVChecker().keyToString(key)+
								"): STARTED");
		//Check local cache and state first
		Object value;
		/**
		value = this.lookupTry(key);
		if (this.getState().checkKeyValue(key, value)){
			return value;
		}
		**/
		//Now try random walks
		Hashtable<WhanauRPCClientStub<T>, Object> parallelResult = this.parallelQuery(this.state.getSetupNumber(),
											numThreads, w, lookupTimeout, "lookupTry", (Integer)queryTimeout, key);
		if (parallelResult == null) {
			this.state.getLog().severe("lookup threads returned nothing");
			return null;
		}
		for (WhanauRPCClientStub<T> index: parallelResult.keySet()) {
			value = parallelResult.get(index);
			if (this.state.getKVChecker().checkKeyRecord(key, value)) {
				this.state.getLog().info("success! value="+this.state.getKVChecker().valueToString(value));
				return value;
			} else {
				this.state.getLog().warning("found a fraudulent value: "+this.state.getKVChecker().valueToString(value));
			}
		}
		this.state.getLog().severe("failed, lookup threads returned nothing");
		return null;
	}
	
	/**
	 * Adds this key/value pair to the node as a published value in the DHT
	 * 
	 * @param value	Serializable	= value to publish in DHT records
	 * @return boolean				= true if success, false otherwise
	 * @throws RemoteException
	 */
	public boolean publishValue(Serializable value) throws RemoteException {
		this.state.addMyValue(value);
		this.state.getLog().info("(value="+this.state.getKVChecker().valueToString(value)+") success!");
		return true;
	}
	
	
}
