package edu.mit.csail.whanausip.commontools.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface for all remote methods that can be called publicly (no ACL)
 * 
 * @author ryscheng
 * @date 2009/02/28
 */
public interface WhanauRefPublic<T> extends Remote{ 
	/***********************
	 * QUERY TOKEN PROTECTED
	 * These methods are relatively-resource intensive and require a valid queryToken
	 * queryTokens are retrieved along with the remote reference after a random walk
	 * queryTokens may only be used once
	 ***********************/
	
	/**
	 * Retrieves the ID of this node at layer i
	 * 
	 * @param queryToken 	long 	= query token
	 * @param i				int 	= layer number
	 * @return Comparable<T> 		= ID at layer i
	 */
	Comparable<T> getID(long queryToken, int i) throws RemoteException;
	
	/**
	 * Retrieves the values of closest successors to the ID in hash space
	 * 
	 * @param queryToken	long 			= query token
	 * @param id			Comparable<T>	= ID to find successors of
	 * @return Object[] 					= array of DHT records that are successors 
	 * @throws RemoteException
	 */
	Object[] successorsSample(long queryToken, Comparable<T> id) throws RemoteException;
	
	/**
	 * Tries a lookup. Requires a queryToken as authorization to perform this work
	 * 
	 * @param queryToken 	long 			= authorization token, retrieved from random walk
	 * @param queryTimeout	int				= timeout value for query() in ms
	 * @param key 			Comparable<T> 	= key to lookup
	 * @return 				Object 			= DHT record containing the value, or null if not found
	 * @throws RemoteException
	 */
	Object lookupTry(long queryToken, int queryTimeout, Comparable<T> key) throws RemoteException;
	
	/****************
	 * PUBLIC METHODS
	 ****************/
	
	/**
	 * Retrieve this node's SHA1 hash of its public key
	 * 
	 * @return String = SHA1 hash (in hex) of this node's public key
	 * @throws RemoteException
	 */
	String getPubKeyHash() throws RemoteException;
	
	/**
	 * Waits for this node to reach specified setup stage.
	 * Then returns true.
	 * 
	 * @return boolean = always true
	 * @throws RemoteException
	 */
	boolean waitStage(int stage) throws RemoteException;
	
	/**
	 * Checks if the value to this key is stored in a 
	 * successor table at this layer
	 * 
	 * @param key 	Comparable<T>	= key to search for
	 * @param layer	int				= layer to search in
	 * @return Object 				= DHT record of this key
	 * @throws RemoteException
	 */
	Object query(Comparable<T> key, int layer) throws RemoteException;
}
