package edu.mit.csail.whanausip.dht.kvchecker;

import java.io.Serializable;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PublicKey;
import java.security.SignedObject;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.WhanauRefPublic;
import edu.mit.csail.whanausip.dht.WhanauKeyState;
import edu.mit.csail.whanausip.dht.WhanauRPCClientStub;
import edu.mit.csail.whanausip.dht.ssl.RMISSLClientSocketFactory;

/**
 * Describes one mode of operation for this DHT
 * Key = String, SHA1 hash of public key
 * Object = SignedObject, containing a WhanauDHTRecord instance
 * In order to verify, check that the public key stored in WhanauDHTRecord,
 * when hashed = key, then verify the signature using that public key.
 * Also check the TTL
 * 
 * @author ryscheng
 * @date 2010/05/04 
 */
public class SigningKVChecker<T> implements KeyValueChecker<T> {
	
	private Logger 			log;
	private WhanauKeyState 	keyState;	//Stores the keys
	private long 			ttl;
	//DEBUG - records number of times records are created and checked
	private int 			numCreates;
	private int 			numChecks;
	
	/**
	 * Initializes a new SigningKVChecker
	 * 
	 * @param log 		Logger 			= stores log entries from this Object
	 * @param keyState 	WhanauKeyState 	= stores the keys
	 * 					Used to create new records and remote references
	 * @param ttl		long			= Time-to-live on all created records
	 * 										(milliseconds)
	 */
	public SigningKVChecker(Logger log, WhanauKeyState keyState, long ttl){
		this.log = log;
		this.keyState = keyState;
		this.ttl = ttl;
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
	 * Returns the default TTL on all records
	 * @return long = ttl
	 */
	public long getTtl() {
		return this.ttl;
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
	 * - key should be SHA1 hash of public key in the record
	 * - record should be signed with the public key
	 * - TTL needs to be valid
	 * 
	 * @param key 		Comparable<T> 	= expected key from the record
	 * @param record 	Object 			= record retrieved from DHT
	 * @return boolean 					= true if valid, false otherwise
	 */
	public boolean checkKeyRecord(Comparable<T> key, Object record) {
		try {
			if (! this.checkRecord(record)) {
				return false;
			}
			return (key.equals(this.getKeyFromRecord(record)));
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return false;
		}
	}
	
	/**
	 * Just checks the validity of the record.
	 * - The record is signed with the included public key
	 * - The TTL has not expired yet
	 * 
	 * @param record Object = record retrieved from DHT
	 * @return boolean 		= record is valid, false otherwise
	 */
	public boolean checkRecord(Object record) {
		synchronized (this) {
			this.numChecks++;
		}
		return (this.checkRecordTTL(record) && this.checkRecordSignature(record));
	}
	
	/**
	 * Checks if the record has expired
	 * Makes sure our current time is before
	 * the creation time + TTL
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return boolean 		= true if valid, false if expired
	 */
	public boolean checkRecordTTL(Object record) {
		try {
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> value = (WhanauDHTRecord<T>) signedObj.getObject();
			if (System.currentTimeMillis() > (value.getCreationTime() + value.getTtl())) {
				this.getLog().warning("Expired record");
				return false;
			}
			return true;
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return false;
		}
	}
	
	/**
	 * Checks if the record is signed with the contained public key
	 * 
	 * @param record 	Object 	= record retrieved from DHT
	 * @return			boolean	= true if signature is valid, false otherwise
	 */
	private boolean checkRecordSignature(Object record){
		try {
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> value = (WhanauDHTRecord<T>) signedObj.getObject();
			PublicKey pubKey = CryptoTool.decodePublicKey(value.getEncodedPublicKey());
			return CryptoTool.verifySignature(signedObj, pubKey);
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return false;
		}
	}
	
	/**
	 * Extracts the key from the record
	 * Just takes a SHA-1 hash of the public key
	 * 
	 * @param record Object = record retrieved from the DHT
	 * @return Comparable<T>= self-certified key corresponding to this record
	 */
	public Comparable<T> getKeyFromRecord(Object record) {
		try {
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> value = (WhanauDHTRecord<T>) signedObj.getObject();
			byte[] encodedKey = value.getEncodedPublicKey();
			return (Comparable<T>) CryptoTool.SHA1toHex(encodedKey);
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
	 * @param record Object 			= record retrieved from the DHT
	 * @return WhanauRPCClientStub<T> 	= remote reference to the public methods
	 */
	public WhanauRPCClientStub<T> getPtrFromRecord(Object record) {
		try {
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> value = (WhanauDHTRecord<T>) signedObj.getObject();
			String pubKeyHash = CryptoTool.SHA1toHex(value.getEncodedPublicKey());
			WhanauRPCClientStub<T> result = new WhanauRPCClientStub<T>(this.getLog(), this.getKeyState().getSslCtx(), 
												pubKeyHash, value.getHost(), value.getPort());
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
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> value = (WhanauDHTRecord<T>) signedObj.getObject();
			return value.getValue();
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
			return null;
		}
	}
	
	/**
	 * Creates a new DHT record that stores the value, my location,
	 * my public key, the current time, and the TTL
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
									CryptoTool.getPublicKey(
											this.getKeyState().getKeys()), 
											System.currentTimeMillis(), this.getTtl());
			SignedObject signedObj = CryptoTool.sign(this.getKeyState().getKeys(), 
											this.getKeyState().getKeyPassword(), record);
			return signedObj;
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
		if (key == null) return "null";
		return key.toString();
	}
	
	/**
	 * Converts the value in a record into a String
	 * 
	 * @param record 	Object	= record retrieved from the DHT
	 * @return 			String	= String representation of value
	 */
	public String valueToString(Object record) {
		Serializable value = this.getValueFromRecord(record);
		if (value != null) {
			return value.toString();
		} else {
			return "InvalidRecord";
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
			SignedObject signedObj = (SignedObject) record;
			WhanauDHTRecord<T> dhtRec = (WhanauDHTRecord<T>) signedObj.getObject();
			return dhtRec.toString();
		} catch (Exception ex) {
			this.getLog().warning(ex.getMessage());
		}
		return "InvalidRecord";
	}
}
