package edu.mit.csail.whanausip.dht.kvchecker;

import java.io.Serializable;

import edu.mit.csail.whanausip.dht.WhanauRPCClientStub;

/**
 * Performs all operations on DHT records, including
 * Checking the self-certifying property of key value pairs
 * Developers can create their own mechanism for choosing
 * key/value pairs, but must implement this Interface
 * to stay compatible with WhanauDHT
 * 
 * @author ryscheng
 * @date 2010/01/12
 */

public interface KeyValueChecker<T> {
	
	/**
	 * Returns the number of times this object created a record
	 * Debugging purposes
	 * 
	 * @return int = number of creates
	 */
	public int getNumCreates();
	
	/**
	 * Returns the number of times this object checked a record
	 * Debugging purposes
	 * 
	 * @return int = number of checks
	 */
	public int getNumChecks();
	
	/**
	 * Checks the validity of the record,
	 * and also checks that the key/value pair is self-certifying
	 * 
	 * @param key 		Comparable<T> 	= expected key from the record
	 * @param record 	Object 			= record retrieved from DHT
	 * @return boolean 					= true if valid, false otherwise
	 */
	public boolean checkKeyRecord(Comparable<T> key, Object record);
	
	/**
	 * Just checks the validity of the record.
	 * 
	 * @param record Object = record retrieved from DHT
	 * @return boolean 		= record is valid, false otherwise
	 */
	public boolean checkRecord(Object record);
	
	/**
	 * Checks if the record has expired
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return boolean 		= true if valid, false if expired
	 */
	public boolean checkRecordTTL(Object record);
	
	/**
	 * Extracts the key from the record
	 * All DHT records must be able to calculate the key using just the record
	 * Easy to do as long as key/value pairs are self-certifying
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return Comparable<T>= self-certified key corresponding to this record
	 */
	public Comparable<T> getKeyFromRecord(Object record);
	
	/**
	 * Retrieves a remote reference from the DHT record
	 * to the public methods. 
	 * (These are the only ones called to results of random walks)
	 * 
	 * @param record Object 			= record retrieved from the DHT
	 * @return WhanauRPCClientStub<T> 	= remote reference to the public methods
	 */
	public WhanauRPCClientStub<T> getPtrFromRecord(Object record);
	
	/**
	 * Returns the value stored in a DHT record
	 * 
	 * @param record Object = record retrieved from DHT
	 * @return Serializable	= value in record
	 */
	public Serializable getValueFromRecord(Object record);
	
	/**
	 * Creates a new DHT record that stores the value, my location,
	 * my public key, the current time, and TTL
	 * 
	 * @param value	Serialiable	= value to store
	 * @param host	String		= my hostname or IP address
	 * @param port	int 		= my WhanauDHT public port
	 * @return		Object		= record to publish in DHT
	 */
	public Object createRecord(Serializable value, String host, int port);
	
	/**
	 * Converts the key into a String
	 * 
	 * @param key 	Comparable<T> 	= key to convert
	 * @return		String			= String representation of key
	 */
	public String keyToString(Comparable<T> key);
	
	/**
	 * Converts the value in a record into a String
	 * 
	 * @param record 	Object	= record retrieved from the DHT
	 * @return 			String	= String representation of value
	 */
	public String valueToString(Object record);
	
	/**
	 * Finds String representation of a DHT record
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return String = string representation of record
	 */
	public String recordToString(Object record);
}
