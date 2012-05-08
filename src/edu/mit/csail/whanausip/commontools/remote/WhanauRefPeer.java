package edu.mit.csail.whanausip.commontools.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedList;

import edu.mit.csail.whanausip.commontools.Pair;

/**
 * Interface for all remote methods that can be called 
 * only by peers in the social network 
 * This ensure undirected edges in random walks along social graph
 * 
 * @author ryscheng
 * @date 2010/07/13
 */
public interface WhanauRefPeer<T> extends Remote {
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
	LinkedList<Pair<Object, Long>> sampleNodes(int numNodes, int steps) 
											throws RemoteException;
}
