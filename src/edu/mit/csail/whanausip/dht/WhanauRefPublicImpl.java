package edu.mit.csail.whanausip.dht;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.WhanauRefPublic;
import edu.mit.csail.whanausip.commontools.threads.*;
import edu.mit.csail.whanausip.dht.ssl.*;


/**
 * Defines all of the methods that can be called publicly
 * Some methods are truly public, like getPubKeyHash
 * Some methods are protected by a queryToken, which is distributed with random walk results
 * 
 * @author ryscheng
 * @date 2009/02/28
 */
public class WhanauRefPublicImpl<T>// extends UnicastRemoteObject 
									implements WhanauRefPublic<T>, MethodThreadInterface {

	private static final 	long serialVersionUID = 1L;
	private 				WhanauState<T> state; 	//This node's state	

	/**
	 * Create a new peer reference, listening on port
	 * This remote object needs to be bound to an RMI registry
	 * 
	 * @param state			WhanauState<T>	= local node's state
	 * @throws RemoteException
	 */
	public WhanauRefPublicImpl(WhanauState<T> state) throws RemoteException{
		//super(state.getLocalPort(), new RMISSLClientSocketFactory(state.getPubKeyHash(), state.getPubKeyHash()), 
		//			new RMISSLServerSocketFactory(state.getPubKeyHash(), null, state.getLog()));
		
		this.state = state;
		//state.info("Initializing a new WhanauRefPublicImpl on "+state.getHashHostPort());
	}
	
	/**
	 * Return this node's state
	 * 
	 * @return WhanauState<T>
	 */
	private WhanauState<T> getState() {
		return state;
	}
	
	/**
	 * Defines all operations that needs to be parallelized.
	 * Currently, only queries to other nodes need to be
	 * 
	 * @param param Object[] = param
	 * 				Object[0] = command
	 * 				Object[1] = remote reference
	 * @return Object = returned result
	 */
	public Object methodThread(Object[] param){
		try {
			WhanauRPCClientStub<T> n = (WhanauRPCClientStub<T>) param[0];
			String command = (String) param[1];
			return n.remoteCall(command, (Comparable<T>)param[2], (Integer) param[3]);
		} catch (Exception e){
			this.getState().getLog().warning("query failed: "+e.getMessage());
		}
		return null;
	}
	
	/**
	 * Used to find the finger to query during lookup attempts.
	 * Look at WhanauDHT paper for further reference
	 * 
	 * @param id0 Comparable<T> = id to compare to
	 * @param key Comparable<T> = key that we're currently looking up
	 * @return Pair<Integer, WhanauRefPublic<T>> = fingers in range
	 */
	private Pair<Integer, WhanauRPCClientStub<T>> chooseFinger(Comparable<T> id0, Comparable<T> key) {
		int numLayers = this.getState().getNumLayers();
		int indices[] = new int[numLayers];
		HashSet<Comparable<T>> currPool;
		
		for (int i = 0; i< numLayers; i++) {
			indices[i] = i;
		}
		//Shuffle indices
		for (int i = 0; i < numLayers; i++) {
			int x = this.getState().nextRandInt(numLayers);
			int y = this.getState().nextRandInt(numLayers);
			int z = indices[x];
			indices[x] = indices[y];
			indices[y] = z;
		}
		//Choose the finger
		for (int i = 0; i < numLayers; i++) {
			int index = indices[i];
			currPool = new HashSet<Comparable<T>>();
			Set<Comparable<T>> fingerIds = this.getState().fingerKeys(index);
			if (fingerIds != null) {
				for (Comparable<T> currId : fingerIds) {
					if ((id0.compareTo((T)key) <= 0) && (currId.compareTo((T)id0) >= 0) && (currId.compareTo((T)key) <= 0)) { 
						currPool.add(currId);
					} else if ((id0.compareTo((T)key) >= 0) && ((currId.compareTo((T)id0) >= 0) || (currId.compareTo((T)key) <= 0))) {
						currPool.add(currId);
					}
				}
			}
			if (! currPool.isEmpty()){
				this.getState().getLog().fine("success! on layer "+index);
				Comparable<T>[] randArray = currPool.toArray((Comparable<T>[]) new Comparable[0]);
				Comparable<T> k = randArray[this.getState().nextRandInt(randArray.length)];
				return new Pair<Integer, WhanauRPCClientStub<T>>(index, this.getState().fingerGet(index, k));
			}
		}
		this.getState().getLog().warning("failed: cannot find any fingers in range");
		return null;
	}
	
	/************************************************
	 *********** QUERY TOKEN PROTECTED***************
	 * These methods are relatively-resource intensive and require a valid queryToken
	 * queryTokens are retrieved along with the remote reference after a random walk
	 * queryTokens may only be used once
	 ************************************************/
	/**
	 * Tries a lookup. Requires a queryToken as authorization to perform this work
	 * 
	 * @param queryToken 	long 			= authorization token, retrieved from random walk
	 * @param queryTimeout	int				= timeout value for query() in ms
	 * @param key 			Comparable<T> 	= key to lookup
	 * @return 				Object 			= DHT record containing the value, or null if not found
	 * @throws RemoteException
	 */
	public Object lookupTry(long queryToken, int queryTimeout, Comparable<T> key) throws RemoteException {
		//Get query token
		if (! this.getState().collectQueryToken(queryToken)) {
			this.getState().getLog().severe("ERROR: Wrong query token: "+queryToken);
			return null;
		}
		//Get all finger keys at layer 0
		Set<Comparable<T>> fingerids0 = this.getState().fingerKeys(0);
		if (fingerids0 == null) {
			this.getState().getLog().warning("failed: no fingers for layer 0");
			return null;
		}
		HashSet<Comparable<T>> idSet = new HashSet<Comparable<T>>(fingerids0);
		idSet.add(key);
		Comparable<T> idArray[] = idSet.toArray((Comparable<T>[])new Comparable[0]);
		int pos, index;
		//Sort and store ordered result in idArray
		Arrays.sort(idArray);
		//Find position of lookup key
		pos = Arrays.binarySearch(idArray, key);
		//Setup a batch request of queries
		MethodThreadBatchRun batch = new MethodThreadBatchRun();
		for (int i=1;i<idArray.length;i++) {
			index = (pos-i);
			if (index < 0){
				index += idArray.length;
			}
			Pair<Integer, WhanauRPCClientStub<T>> finger = this.chooseFinger(idArray[index],key);
			if (finger != null) {
				batch.addThread(Integer.toString(i), this, finger.getSecond(), "query", key, finger.getFirst());				
			}
		}
		if (batch.getNumThreads() > 0) {
			batch.joinTermination(queryTimeout);
			Hashtable<String,Object> results = batch.getFinalResults();
			//Find a self-certifying result
			for (String i : results.keySet()){
				Object value = results.get(i);
				if (this.getState().getKVChecker().checkKeyRecord(key, value)){
					this.getState().getLog().fine("success! returns: "+this.getState().getKVChecker().valueToString(value));
					return value;
				} else {
					this.getState().getLog().warning("lookupTry: query result failed record checking");
				}
			}
			this.getState().getLog().warning("failed lookupTry: "+batch.getNumThreads()+" threads to query fingers returned "+results.size()+" results");
			return null;
		} else {
			this.getState().getLog().warning("failed lookupTry: no suitable fingers to query");
			return null;
		}
	}
	
	/**
	 * Retrieves the ID of this node at layer i
	 * 
	 * @param queryToken 	long 	= query token
	 * @param i				int 	= layer number
	 * @return Comparable<T> 		= ID at layer i
	 */
	public Comparable<T> getID(long queryToken, int layer) throws RemoteException {
		//Get query token
		if (! this.getState().collectQueryToken(queryToken)) {
			this.getState().getLog().severe("ERROR: wrong query token: "+queryToken);
			return null;
		}
		Comparable<T> result;
		//Wait until previous layer has completed everything
		int depStage = (2*layer)+3;
		try	{
			this.getState().waitForSetupStage(depStage);
		}
		catch(InterruptedException e) {	
			this.getState().getLog().severe("fail due to InterruptedException on wait for setupStage "+depStage);
			return null;
		}
		result = this.getState().idGet(layer);
		if (result == null) {
			this.getState().getLog().severe("returned NULL. Incorrect Behavior!");
		}
		this.getState().getLog().fine("success: returns "+this.getState().getKVChecker().keyToString(result));
		return result;
	}
	
	/**
	 * Retrieves the values of closest successors to the ID in hash space
	 * 
	 * @param queryToken	long 			= query token
	 * @param id			Comparable<T>	= ID to find successors of
	 * @return Object[] 					= array of DHT records that are successors 
	 * @throws RemoteException
	 */
	public Object[] successorsSample(long queryToken, Comparable<T> id) throws RemoteException {
		//Get query token
		if (! this.getState().collectQueryToken(queryToken)) {
			this.getState().getLog().severe("ERROR: Wrong query token: "+queryToken);
			return null;
		}
		//Wait until we have our database
		try	{
			this.getState().waitForSetupStage(WhanauState.STAGE2);
		}
		catch(InterruptedException e) {	
			this.getState().getLog().severe("fail due to InterruptedException on wait for setupStage 2");
			return null;
		}
		//Sample successors
		Object result[] = new Object[WhanauDHTConstants.SUCCESSORS_SAMPLE_SIZE];
		Set<Comparable<T>> keySet = this.getState().getDatabase().keySet();
		HashSet<Comparable<T>> newKeySet = new HashSet<Comparable<T>>(keySet);
		newKeySet.add(id);
		Object[] keyArray = newKeySet.toArray((Comparable<T>[])new Comparable[0]);
		int pos, i;

		//Sort
		Arrays.sort(keyArray);
		//Find where our id is in this array
		pos = Arrays.binarySearch(keyArray, id);
		//Copy first few into our results
		for (i=0;i<WhanauDHTConstants.SUCCESSORS_SAMPLE_SIZE;i++) {
			Comparable<T> key = (Comparable<T>) keyArray[(pos+i+1) % keyArray.length];
			result[i] = this.getState().databaseGet(key);
		}
		this.getState().getLog().fine("success!");
		return result;
	}
	
	
	
	/************************************************
	 ************** PUBLIC METHODS ******************
	 ************************************************/
	/**
	 * Retrieve this node's SHA1 hash of its public key
	 * 
	 * @return String = SHA1 hash (in hex) of this node's public key
	 * @throws RemoteException
	 */
	public String getPubKeyHash() throws RemoteException {
		this.getState().getLog().fine("success");
		return this.getState().getPubKeyHash();
	}
	
	/**
	 * Waits for this node to reach specified setup stage.
	 * Then returns true.
	 * 
	 * @return boolean = always true
	 * @throws RemoteException
	 */
	public boolean waitStage(int stage) throws RemoteException {
		try	{
			this.getState().waitForSetupStage(stage);
		}
		catch(InterruptedException e) {	
			this.getState().getLog().severe("fail due to InterruptedException on wait for setupStage 2");
			return false;
		}
		this.getState().getLog().fine("success");
		return true;
	}
	
	/**
	 * Checks if the value to this key is stored in a 
	 * successor table at this layer
	 * 
	 * @param key 	Comparable<T>	= key to search for
	 * @param layer	int				= layer to search in
	 * @return Object 				= DHT record of this key
	 * @throws RemoteException
	 */
	public Object query(Comparable<T> key, int layer) throws RemoteException {
		Object result;
		Hashtable<Comparable<T>,Object> succTable = this.getState().succGet(layer);
		//Check if successor table at this layer exists
		if (succTable == null) {
			this.getState().getLog().warning("no successor table for that layer="+layer);
			return null;
		}
		//Check if it exists
		result = succTable.get(key);
		if (result != null)
			this.getState().getLog().fine("returns: "+this.getState().getKVChecker().valueToString(result));
		else
			this.getState().getLog().fine("returns: null");
		return result;
	}

}
