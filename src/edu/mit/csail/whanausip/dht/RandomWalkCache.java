package edu.mit.csail.whanausip.dht;

import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Set;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.threads.*;

/**
 * Stores a number of pre-fetched random walks
 * Performs random in batches (lazy)
 * 
 * @author ryscheng
 * @date 2009/10/19
 */
public class RandomWalkCache<T> implements MethodThreadInterface{
	private WhanauState<T> 								state;
	/**
	 * Number of random walks to pre-fetch per distance
	 */
	private int											depth;
	/**
	 * Index = number of steps away, value = linked list of random walks
	 * a random walk result is a pair - (DHT record, queryToken)
	 */
	private ArrayList<LinkedList<Pair<Object, Long>>> 	randWalks;
	/**
	 * Index = number of steps away, value = thread to fetch this step exists
	 */
	private ArrayList<Boolean> 							openRequest;
	/**
	 * Counts number of times fillCache has been called on this level
	 */
	private int[]										fillCacheCount;
	
	/**
	 * Creates a new random walk cache
	 * 
	 * @param state WhanauState<T> 	= state of the WhanauDHT node
	 * @param depth int				= number of random walks to pre-fetch
	 */
	public RandomWalkCache(WhanauState<T> state, int depth){
		this.state = state;
		this.reset(depth);
	}
	
	/**
	 * Clears the cache and sets a new depth
	 * 
	 * @param depth int = new depth
	 */
	public synchronized void reset(int depth){
		this.depth = depth;
		this.randWalks = new ArrayList<LinkedList<Pair<Object, Long>>>();
		this.openRequest = new ArrayList<Boolean>();
		fillCacheCount = new int[WhanauDHTConstants.W + 1];
	}
	
	/**
	 * Returns the entire cache of random walks
	 * @return ArrayList<LinkedList<Pair<Object, Long>>>
	 */
	private ArrayList<LinkedList<Pair<Object, Long>>> getRandWalks(){
		return this.randWalks;
	}
	
	/**
	 * Returns the entire tracker for open requests
	 * @return
	 */
	private ArrayList<Boolean> getOpenRequest(){
		return this.openRequest;
	}
	
	/**
	 * Returns the chain of random walks for a given distance away
	 * If the cache isnt big enough, make room
	 * 
	 * @param i int 							= number of steps from this node
	 * @return 	LinkedList<Pair<Object, Long>> 	= random walks from that distance
	 */
	private synchronized LinkedList<Pair<Object, Long>> getIndex(int i){
		this.getRandWalks().ensureCapacity(i+1);
		int expand = i+1-this.getRandWalks().size();
		for (int j=0;j<expand;j++){
			this.getRandWalks().add(null);
		}
		LinkedList<Pair<Object, Long>> result = this.getRandWalks().get(i);
		if (result == null){
			result = new LinkedList<Pair<Object, Long>>();
			this.getRandWalks().set(i,result);
		}
		return result;
	}
	
	/**
	 * Returns whether there already exists a thread to retrieve 
	 * random walks from this distance away
	 * if the tracker list isnt big enough, make more room
	 * 
	 * @param i int 	= number of steps from this node
	 * @return 	boolean = true if thread exists, false otherwise
	 */
	private synchronized boolean hasOpenRequest(int i){
		this.getOpenRequest().ensureCapacity(i+1);
		int expand = i+1-this.getOpenRequest().size();
		for (int j=0;j<expand;j++){
			this.getOpenRequest().add(null);
		}
		Boolean result = this.getOpenRequest().get(i);
		if (result == null){
			result = new Boolean(false);
			this.getOpenRequest().set(i,result);
		}
		return result;
	}
	
	/**
	 * Checks if there exists any threads fetching random walks
	 * with a distance less than or equal to the parameter
	 * 
	 * @param compare	int		= number of steps from this node
	 * @return 			boolean	= true if threads exist, false otherwise
	 */
	private synchronized boolean hasLowerOpenRequest(int compare){
		for (int i=1; i<=compare; i++){
			if (this.hasOpenRequest(i)) return true;
		}
		return false;
	}
	
	/**
	 * Mark down whether a thread exists for this index
	 * 
	 * @param i		int		= number of steps from this node
	 * @param value boolean = true if thread exists, false otherwise
	 */
	private synchronized void setOpenRequest(int i, boolean value){
		//Make room first in case cache isnt big enough
		this.hasOpenRequest(i);
		this.getOpenRequest().set(i, value);
	}
	
	/**
	 * Gets the depth of this cache
	 * 
	 * @return int = depth
	 */
	public int getDepth(){
		return this.depth;
	}
	
	/**
	 * Returns the state of the WhanauDHT node
	 * 
	 * @return WhanauState<T> = node state
	 */
	private WhanauState<T> getState(){
		return this.state;
	}
	
	/**
	 * Assuming our cache is sufficiently filled, 
	 * this will return as results as it can.
	 * No remote calls to fetch more, just best effort
	 * 
	 * @param numNodes	int	= number of desired results
	 * @param steps		int = number of steps from this node
	 * @return LinkedList<Pair<Object, Long>> = random walk results
	 */
	private synchronized LinkedList<Pair<Object, Long>> 
									retrieveRandWalks(int numNodes, int steps){
		LinkedList<Pair<Object, Long>> result = new LinkedList<Pair<Object, Long>>();
		LinkedList<Pair<Object, Long>> currList = this.getIndex(steps);
		int stop = Math.min(currList.size(), numNodes);
		int index;
		
		for (int i=0; i<stop; i++){
			index = this.state.nextRandInt(currList.size());
			result.addLast(currList.remove(index));
		}
		return result;
	}
	
	/**
	 * This will fetch the random walk cache at a particular level.
	 * The number it will fetch is the number requested, plus the fill the depth of the cache level
	 * Make sure that this.hasOpenRequest(steps) is false before calling fillCache(...) 
	 * 
	 * @param numNodes 	int = Number of random walks requested 
	 * @param steps 	int = Number of steps in random walk
	 */
	private void fillCache(int numNodes, int steps){
		MethodThreadBatchRun batch = new MethodThreadBatchRun();
		//lock the cache
		synchronized (this) {
			if (numNodes > this.getIndex(steps).size()) {
				this.fillCacheCount[steps]++;
				//if (steps == WhanauDHTConstants.W) System.out.println(this.toString());
				//Indicate that we have threads filling up the cache at this level
				this.setOpenRequest(steps,true);
				//Randomly distribute requests to active peers
				Set<WhanauRPCClientStub<T>> peers = this.getState().getActivePeers();
				int numPeers = peers.size();
				int[] randWalkRequest = new int[numPeers];
				LinkedList<Pair<Object, Long>> currList = this.getIndex(steps);
	
				if (numPeers > 0) {
					int limit;
					if (this.fillCacheCount[steps] == 1) 
						limit = (this.getDepth()*(WhanauDHTConstants.W - steps + 1));//+numNodes;
					else 
						limit = this.getDepth()+numNodes;
					for (int i=currList.size();i<limit;i++){
						randWalkRequest[this.getState().nextRandInt(numPeers)]++;
					}
					//Send random walk batch requests
					int i=0;
					for (WhanauRPCClientStub<T> p : peers){
						if (randWalkRequest[i] > 0){
							batch.addThread(Integer.toString(i), this,  p, 
												randWalkRequest[i], steps);
						}
						i++;
					}
				}
			}
		}
		if (batch.getNumThreads() > 0) {
			batch.joinTermination((WhanauDHTConstants.RANDWALK_PERSTEP_TIMEOUT+WhanauDHTConstants.RANDWALK_PERSTEP_PROCTIME)*steps - 
						WhanauDHTConstants.RANDWALK_PERSTEP_PROCTIME);
			batch.getFinalResults();
		}

		this.setOpenRequest(steps,false);
	}
	
	/**
	 * This is the main visible method of RandomWalkCache
	 * It will retrieve a number of random walks
	 * 
	 * @param numNodes	int = number of desired random walks
	 * @param steps		int = number of steps in random walk
	 * @return LinkedList<Pair<Object,Long>> = List of random walk results
	 * 						random walk result is a pair (DHT record, queryToken)
	 * 						returns null if the parameters are out of bounds
	 * 							or if we cannot retrieve the desired number
	 */
	public LinkedList<Pair<Object,Long>> sampleNodes(int numNodes, int steps){
		LinkedList<Pair<Object,Long>> currList;
		
		synchronized (this) {
			this.getRandWalks().ensureCapacity(steps+1);
			//Remove expired entries
			currList = this.getIndex(steps);
			for (Pair<Object,Long> record : currList) {
				if (! this.getState().getKVChecker().checkRecordTTL(record.getFirst())) {
					currList.remove(record);
				}
			}
			//Check bounds
			if (steps < 0) {
				this.state.getLog().warning("(numNodes="+numNodes+",steps="+steps+") Error! steps < 0");
				return null;
			}
			if (steps > WhanauDHTConstants.W) {
				this.state.getLog().warning("(numNodes="+numNodes+",steps="+steps+") Error! steps > "+WhanauDHTConstants.W+"=maximum");
				return null;
			}
			//if (numNodes > (2*this.getDepth())) { 
			//	this.state.getLog().warning("(numNodes="+numNodes+",steps="+steps+") Error! numNodes > 2*"+this.getDepth()+"=cache depth");
			//	return null;
			//}
			//Check endcase, return this node's record
			if (steps == 0) {
				currList = new LinkedList<Pair<Object, Long>>();
				for (int i=0; i < numNodes; i++){
					currList.addLast(new Pair<Object,Long>(
								this.getState().getRandomMyRecord(),
								this.getState().generateQueryToken()));
				}
				return currList;
			}
			//If we have enough in the cache, just satisfy the request
			if (this.getIndex(steps).size() >= numNodes){
				return this.retrieveRandWalks(numNodes, steps);
			}
		}
		//If we have no active peers, just give up
		if (this.getState().getActivePeers().size() <= 0) {
			this.state.getLog().warning("No active peers: quitting");
			return null;
		}
		//If we don't have enough, fetch some more
		//Dont proceed if there is already random walk RMI requests out		
		for (int i=1;i<=steps;i++) {
			synchronized (this) {
				while (this.hasLowerOpenRequest(i)) {
					try {
						this.wait();
					}catch(InterruptedException e) {
						this.getState().getLog().warning("Interrupted Wait");
					}
				}
			}
			this.fillCache(numNodes, i);
			this.lockedNotifyAll();
		}
		
		//Our pre-fetching should have enough, try again
		//If we can't get it, just return null = failure
		if (this.getIndex(steps).size() >= numNodes) {
			return this.retrieveRandWalks(numNodes, steps);
		} else {
			this.state.getLog().warning("Tried retrieving, still not enough. Returning null");
			return null;
		}
	}
	
	/**
	 * I'm not sure if this is actually the correct thing to do.
	 * I keep large parts of the class unlocked for concurrency,
	 * so this is used to notify waiting threads in unlocked code
	 */
	public synchronized void lockedNotifyAll(){
		this.notifyAll();
	}

	/**
	 * In order to parallelized remote method invocations to different
	 * peers in recursive random walk,
	 * this method is called by different threads
	 * 
	 * @param Object[] 	= array of parameters
	 * 					= Object[0] WhanauPeer 	= remote reference
	 * 					= Object[1] int			= number of random walks
	 * 					= Object[2] int 		= number of steps in random walk
	 * @return Object = Boolean = true if success, false otherwise
	 */
	public Object methodThread(Object[] parameters){
		LinkedList<Pair<Object, Long>> result;
		WhanauRPCClientStub<T> p = (WhanauRPCClientStub<T>)parameters[0];
		int numNodes = (Integer) parameters[1];
		int steps = (Integer) parameters[2];
		//Send RMI
		try{
			if (p == null) {
				throw new RemoteException("Peer Reference is null");
			} 
			result = (LinkedList<Pair<Object, Long>>) p.remoteCall(WhanauDHTConstants.SAMPLENODES_CMD, numNodes, steps-1);
			//Merge with our own list
			this.lockedAdd(result, steps);
			return new Boolean(true);
		}catch (Exception e){
			p.setActiveStatus(false);
			this.getState().getLog().warning("Peer failed at remote call to sampleNodes(): "+
											p.getHashHostPort()+":"+e.getMessage());
		}
		return new Boolean(false);
	}
	
	/**
	 * Takes the result of a call to sampleNodes(...) and merges it with our cache
	 * Checks each result is self-certifying and properly formed
	 * Must be locked to protect cache
	 * 
	 * @param toAdd LinkedList<Pair<Object, Long>> 	= result from peer.sampleNodes(...)
	 * @param i 	int								= current level in cache to add it to (steps)
	 */
	public synchronized void lockedAdd(LinkedList<Pair<Object, Long>> toAdd, int i) {
		if (toAdd != null) {
			LinkedList<Pair<Object, Long>> workingList = (LinkedList<Pair<Object, Long>>) toAdd.clone();
			for (Pair<Object,Long> record : workingList) {
				if (! this.getState().getKVChecker().checkRecord(record.getFirst())) {
					workingList.remove(record);
				}
			}
			this.getIndex(i).addAll(workingList);
		}
	}
	
	/**
	 * Prints a string representation of this cache
	 * 
	 * @return String
	 */
	public synchronized String toString() {
		String result="RandomWalkCache: depth="+this.getDepth()+"\n";
		result+="Level - #items - #fillCache()\n";
		for (int i=WhanauDHTConstants.W;i>=0;i--) {
			LinkedList<Pair<Object, Long>> currList = this.getIndex(i);
			result += "\t "+i+" - "+currList.size()+" - "+this.fillCacheCount[i]+"\n";
		}
		return result;
	}

}
