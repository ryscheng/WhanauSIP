package edu.mit.csail.whanausip.dht;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;

import edu.mit.csail.whanausip.commontools.Pair;
import edu.mit.csail.whanausip.commontools.remote.WhanauRefPeer;
import edu.mit.csail.whanausip.dht.ssl.*;

/**
 * Interface for all the methods than can only be called by peers on a social network
 * These peers are stored in state.peers
 * 
 * 
 * @author ryscheng
 * @date 2010/07/23
 */
public class WhanauRefPeerImpl<T> //extends UnicastRemoteObject 
									implements WhanauRefPeer<T> {

	private static final 	long 			serialVersionUID = 8243992235978081413L;
	private 				WhanauState<T> 	state;	//state of this node
	
	/**
	 * Create a new peer reference, listening on port+1
	 * This remote object needs to be bound to an RMI registry
	 * 
	 * @param state			WhanauState<T>	= local node's state
	 * @throws RemoteException
	 */
	public WhanauRefPeerImpl(WhanauState<T> state) throws RemoteException{
		//super(state.getLocalPort()+1, new RMISSLClientSocketFactory(state.getPubKeyHash(), state.getPubKeyHash()), 
		//			new RMISSLServerSocketFactory(state.getPubKeyHash(), state.getAllPeerKeys(), state.getLog()));
		
		this.state = state;
		//state.info("Initializing a new WhanauRefPeerImpl on "+state.getHashHostPort());
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
	 ************** PEER METHODS ******************
	 **/
	/**
	 * Returns a batch of random walk results
	 * 
	 * @param numNodes	int 					= number of desired random walks
	 * @param steps 	int 					= number of steps to take in each random walk
	 * @return LinkedList<Pair<Object, Long>> 	= list of random walk results;
	 * 											each pair contains a DHT record
	 * 											and a corresponding query token
	 * @throws RemoteException
	 */
	public LinkedList<Pair<Object, Long>> sampleNodes(int numNodes, int steps) throws RemoteException{
		LinkedList<Pair<Object, Long>> result;
		/**
		try {
			this.getState().waitForSetupStage(WhanauState.STAGE1);
		} catch(InterruptedException e) {
			this.getState().getLog().severe("Fail due to InterruptedException on wait for setupStage 1");
			return null;
		}
		**/
		result = this.getState().sampleNodes(numNodes, steps);
		this.getState().getLog().fine("(numNodes="+numNodes+", steps="+steps+") success!");
		return result;
	}
}
