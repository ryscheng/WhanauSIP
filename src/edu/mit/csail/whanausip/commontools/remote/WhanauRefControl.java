package edu.mit.csail.whanausip.commontools.remote;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.KeyStore;

import edu.mit.csail.whanausip.dht.WhanauState;
import edu.mit.csail.whanausip.dht.kvchecker.KeyValueChecker;

/**
 * Interface for all remote methods that can be called
 * by a remote administrator.
 * These methods control the node's behavior directly
 * 
 * @author ryscheng
 * @date 2010/07/08
 */
public interface WhanauRefControl<T> extends Remote {
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
	WhanauRefControl<T> createNode(Serializable value, 
									KeyValueChecker<T> kvChecker,
									String host, int port, KeyStore keys, 
									String keyPasswd) throws Exception;
	
	/**
	 * Returns the state of this node
	 * 
	 * @return WhanauState<T> = state
	 * @throws RemoteException
	 */
	WhanauState<T> getState() throws RemoteException;
	
	/**
	 * Returns string representation of the state
	 * 
	 * @return String = string representation
	 * @throws RemoteException
	 */
	String getStateStr() throws RemoteException;
	
	/**
	 * Returns a string containing all entries in the log
	 * 
	 * @return String = log
	 * @throws RemoteException
	 */
	String getLogStr() throws RemoteException;
	
	/**
	 * Adds a peer to this node in the social graph
	 * 
	 * @param pubKeyHash	String	= peer's SHA1 hash of public key
	 * @param host			String	= peer's hostname
	 * @param port			int		= peer's WhanauDHT port
	 * @return boolean 				= true if peer is added, false if fail
	 * @throws RemoteException
	 */
	boolean addPeer(String pubKeyHash, String host, int port) 
									throws RemoteException;
	
	/**
	 * Removes all peers from the state
	 * 
	 * @return true always
	 * @throws RemoteException
	 */
	boolean removeAllPeers() throws RemoteException;
	
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
	boolean setSetupStage(int stage) 
									throws RemoteException;
	
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
	boolean runSetupPart1Thread(int w, int rd, int rf, int rs) 
									throws RemoteException;
	
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
	boolean runSetupPart2Thread(int w, int rd, int rf, int rs) 
									throws RemoteException;
	
	/**
	 * Joins the setup thread. Blocks until setup is done
	 * 
	 * @return boolean = true if successful, false if not
	 * @throws RemoteException
	 * @throws InterruptedException
	 */
	boolean joinSetupThread() throws RemoteException, InterruptedException;
	
	/**
	 * Returns the result of setup()
	 * 
	 * @return long = time to run setup in milliseconds, 0 if failure
	 * @throws RemoteException
	 */
	long getSetupThreadResult() throws RemoteException;
	
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
	Object lookup(int lookupTimeout, int queryTimeout, int numThreads, int w, Comparable<T> key) throws RemoteException;
	
	/**
	 * Adds this key/value pair to the node as a published value in the DHT
	 * 
	 * @param value	Serializable	= value to publish in DHT records
	 * @return boolean				= true if success, false otherwise
	 * @throws RemoteException
	 */
	boolean publishValue(Serializable value) throws RemoteException;
}
