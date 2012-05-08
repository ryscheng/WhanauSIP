package edu.mit.csail.whanausip.commontools;

import java.io.Serializable;

/**
 * Wrapper stores all aspects of a value stored in Whanau DHT,
 * including host:port, value, creation time, TTL
 * 
 * @author ryscheng
 * @date 2010/05/19
 */
public class WhanauDHTRecord<T> implements Serializable{
	private static final long serialVersionUID = 6578439497475803125L;
	//Required
	private Serializable 	value;				//Value to be published
	private String 			host;				//Host of owner running WhanauDHT
	private int 			port;				//Port of owner running WhanauDHT
	//Optional
	private byte[] 			encodedPublicKey;	//public key of owner
	private long 			creationTime;		//Time record was created
	private long 			ttl;				//Time to live on this record
	
	/**
	 * Creates a new record
	 * 
	 * @param value				Serializable	= Value to be published
	 * @param host				String			= Host of owner running WhanauDHT
	 * @param port				int			 	= Port of owner running WhanauDHT
	 * @param encodedPublicKey	byte[]			= public key of owner
	 * @param creationTime		long			= Time record was created
	 * @param ttl				long			= Time to live on this record
	 */
	public WhanauDHTRecord(Serializable value, String host, int port, 
							byte[] encodedPublicKey, long creationTime, long ttl) {
		this.setValue(value);
		this.setHost(host);
		this.setPort(port);
		this.setEncodedPublicKey(encodedPublicKey);
		this.setCreationTime(creationTime);
		this.setTtl(ttl);
	}
	
	/**
	 * Gets the record owner's hostname
	 * 
	 * @return String = hostname
	 */
	public String getHost() {
		return host;
	}
	
	/**
	 * Set the record owner's hostname
	 * 
	 * @param host String = hostname
	 */
	public void setHost(String host) {
		this.host = host;
	}
	
	/**
	 * Gets the record owner's port
	 * 
	 * @return int = port
	 */
	public int getPort() {
		return port;
	}
	
	/**
	 * Sets the record owner's port
	 * 
	 * @return int = port
	 */
	public void setPort(int port) {
		this.port = port;
	}
	
	/**
	 * Gets the stored value
	 * 
	 * @return Serializable = stored value
	 */
	public Serializable getValue() {
		return value;
	}
	
	/**
	 * Sets the stored value
	 * 
	 * @param value Serializable = stored value
	 */
	public void setValue(Serializable value) {
		this.value = value;
	}
	
	/**
	 * Gets the encoded public key
	 * 
	 * @return byte[] = public key
	 */
	public byte[] getEncodedPublicKey() {
		return encodedPublicKey;
	}
	
	/**
	 * Sets the encoded public key
	 * 
	 * @param encodedPublicKey byte[] = public key 
	 */
	public void setEncodedPublicKey(byte[] encodedPublicKey) {
		this.encodedPublicKey = encodedPublicKey;
	}
	
	/**
	 * Gets the creation time
	 * 
	 * @return long = creation time of record
	 */
	public long getCreationTime() {
		return creationTime;
	}
	
	/**
	 * Sets the creation time
	 * 
	 * @param creationTime long = creatio time
	 */
	public void setCreationTime(long creationTime) {
		this.creationTime = creationTime;
	}
	
	/**
	 * Gets the TTL
	 * 
	 * @return long = TTL
	 */
	public long getTtl() {
		return ttl;
	}
	
	/**
	 * Sets the TTL
	 * 
	 * @param ttl = TTL
	 */
	public void setTtl(long ttl) {
		this.ttl = ttl;
	}
	
	/**
	 * Returns a string representation of this record
	 * 
	 * @return String = string representation of record
	 */
	public String toString() {
		String result = "[";
		if (this.getValue() != null) result+= "value="+this.getValue().toString();
		else result+= "value=null";
		if (this.getHost() != null) result+= ",Host="+this.getHost();
		else result+= ",host=null";
		result+= ",port="+this.getPort();
		try {
			if (this.getEncodedPublicKey() != null) 
				result+= ",key="+CryptoTool.SHA1toHex(this.getEncodedPublicKey());
			else 
				result+= ",key=null";
		} catch (Exception ex) {
			result += ",key=error";
		}
		result+= ",created="+this.getCreationTime();
		result+= ",ttl="+this.getTtl();
		result+="]";
		return result;
	}


}
