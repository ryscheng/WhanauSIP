package edu.mit.csail.whanausip.dht.kvchecker;

import java.io.Serializable;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.WhanauRefPublic;
import edu.mit.csail.whanausip.dht.*;

/**
 * Describes one mode of operation for this DHT
 * Key = SHA1 hash of the object.toString()
 * Object = anything
 * No TTL
 * 
 * @author ryscheng
 * @date 2010/01/12
 *
 */
public class HashingKVChecker<T> implements KeyValueChecker<T> {
	
	private Logger 			log;
	private WhanauKeyState 	keyState;	//Stores the keys
	//DEBUG - records number of times records are created and checked
	private int 			numCreates;
	private int 			numChecks;
	
	/**
	 * Initializes a new HashingKVChecker
	 * 
	 * @param log 		Logger 			= stores log entries from this Object
	 * @param keyState 	WhanauKeyState 	= stores the keys
	 * 					Used to create new records and remote references
	 */
	public HashingKVChecker(Logger log, WhanauKeyState keyState){
		this.log = log;
		this.keyState = keyState;
		//DEBUG
		this.numCreates = 0;
		this.numChecks = 0;
	}
	
	/**
	 * Returns the logger
	 * @return Logger
	 */
	private Logger getLog() {
		return this.log;
	}
	
	/**
	 * Returns the node's key state
	 * @return WhanauKeyState
	 */
	private WhanauKeyState getKeyState(){
		return this.keyState;
	}
	
	/**
	 * Returns the number of times this object created a record
	 * Debugging purposes
	 * 
	 * @return int = number of creates
	 */
	public synchronized int getNumCreates() {
		return this.numCreates;
	}
	
	/**
	 * Returns the number of times this object checked a record
	 * Debugging purposes
	 * 
	 * @return int = number of checks
	 */
	public synchronized int getNumChecks() {
		return this.numChecks;
	}
	
	/**
	 * Checks the validity of the record,
	 * and also checks that the key/value pair is self-certifying
	 * (key should be SHA1 hash of the value in the record)
	 * 
	 * @param key 		Comparable<T> 	= expected key from the record
	 * @param record 	Object 			= record retrieved from DHT
	 * @return boolean 					= true if valid, false otherwise
	 */
	public boolean checkKeyRecord(Comparable<T> key, Object record) {
		try {
			return key.equals(this.getKeyFromRecord(record));
		} catch (Exception ex){
			this.getLog().warning(ex.getMessage());
			return false;
		}
	}
	
	/**
	 * Just checks the validity of the record.
	 * In this case, the record must be an instance of WhanauDHTRecord
	 * 
	 * @param record Object = record retrieved from DHT
	 * @return boolean 		= record is valid, false otherwise
	 */
	public boolean checkRecord(Object record) {
		synchronized (this) {
			this.numChecks++;
		}
		return (record instanceof WhanauDHTRecord<?>);
	}
	
	/**
	 * Checks if the record has expired
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return boolean 		= always true
	 */
	public boolean checkRecordTTL(Object record) {
		return true;
	}
	
	/**
	 * Extracts the key from the record
	 * Just takes a SHA-1 hash of the value
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return Comparable<T>= self-certified key corresponding to this record
	 */
	public Comparable<T> getKeyFromRecord(Object record) {
		try {
			Object value = this.getValueFromRecord(record);
			if (value != null) 
				return (Comparable<T>) CryptoTool.SHA1toHex(
										(String)this.getValueFromRecord(record));
			else 
				return null;
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Retrieves a remote reference from the DHT record
	 * to the public methods. 
	 * (These are the only ones called to results of random walks)
	 * 
	 * @param record Object 		= record retrieved from the DHT
	 * @return WhanauRPCClientStub<T> 	= remote reference to the public methods
	 */
	public WhanauRPCClientStub<T> getPtrFromRecord(Object record) {
		try {
			WhanauDHTRecord<T> structRec = (WhanauDHTRecord<T>) record;
			String pubKeyHash = CryptoTool.SHA1toHex(structRec.getEncodedPublicKey());
			WhanauRPCClientStub<T> result = new WhanauRPCClientStub<T>(this.getLog(), this.getKeyState().getSslCtx(), 
												pubKeyHash, structRec.getHost(), structRec.getPort());
			
			return result;
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Returns the value stored in a DHT record
	 * 
	 * @param record Object = record retrieved from DHT
	 * @return Serializable	= value in record
	 */
	public Serializable getValueFromRecord(Object record) {
		try {
			WhanauDHTRecord<T> structRec = (WhanauDHTRecord<T>) record;
			return structRec.getValue();
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Creates a new DHT record that stores the value, my location,
	 * my public key, and the current time
	 * 
	 * @param value	Serialiable	= value to store
	 * @param host	String		= my hostname or IP address
	 * @param port	int 		= my WhanauDHT public port
	 * @return		Object		= record to publish in DHT
	 */
	public Object createRecord(Serializable value, String host, int port) {
		synchronized (this) {
			this.numCreates++;
		}
		try {
			WhanauDHTRecord<T> record = new WhanauDHTRecord<T>(value, host, port,
									CryptoTool.getPublicKey(this.getKeyState().getKeys()), 
									System.currentTimeMillis(), 0);
			return record;
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Converts the key into a String
	 * 
	 * @param key 	Comparable<T> 	= key to convert
	 * @return		String			= String representation of key
	 */
	public String keyToString(Comparable<T> key) {
		try {
			return (String) key;
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Converts the value in a record into a String
	 * 
	 * @param record 	Object	= record retrieved from the DHT
	 * @return 			String	= String representation of value
	 */
	public String valueToString(Object record) {
		try {
			return (String) this.getValueFromRecord(record);
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Finds String representation of a DHT record
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return String = string representation of record
	 */
	public String recordToString(Object record) {
		try {
			WhanauDHTRecord<T> structRec = (WhanauDHTRecord<T>) record;
			return record.toString();
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}

}
