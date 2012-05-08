package edu.mit.csail.whanausip.dht;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.Hashtable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import edu.mit.csail.whanausip.commontools.*;

/**
 * Stores the KeyStore and SSL contexts of a WhanauDHT node instance
 * 
 * @author ryscheng
 * @date 2010/07/18
 */
public class WhanauKeyState {
	/**
	 * These are a slight hack in order to get RMISSLClientSocketFactory to
	 * use the proper keys during SSL handshakes.
	 * Store the data in a static variable, so the keys are not
	 * sent over the network
	 */
	private static 	Hashtable<String, SSLContext> 	sslContextStore;
	private static 	SSLContext 						mostRecentSSLContext;
	/**
	 * Local instance variables storing the keys, key password,
	 * SSL Context, and hash of public key
	 */
	private 		String 							pubKeyHash;
	private 		KeyStore 						keys;
	private 		String 							keyPassword;
	private 		SSLContext 						sslCtx;
	
	/**
	 * Initiates a new WhanauKeyState with the given keys
	 * 
	 * @param keys 		KeyStore  	= keystore with private and public keys
	 * @param password	String		= password to unlock keystore
	 * @throws NoSuchAlgorithmException
	 * @throws UnrecoverableKeyException
	 * @throws KeyStoreException
	 * @throws KeyManagementException
	 * @throws UnsupportedEncodingException
	 */
	public WhanauKeyState(KeyStore keys, String password) throws NoSuchAlgorithmException, 
											UnrecoverableKeyException, KeyStoreException, 
											KeyManagementException, UnsupportedEncodingException {
		this.setPubKeyHash(CryptoTool.getPublicKeyHash(keys));
		this.setKeys(keys);
		this.setKeyPassword(password);
		SSLContext context = SSLContext.getInstance(WhanauDHTConstants.SECURESOCKETPROTOCOL);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(WhanauDHTConstants.KEY_MGMT_ALG);
		kmf.init(this.getKeys(), this.getKeyPassword().toCharArray());
		context.init(kmf.getKeyManagers(), CryptoTool.getAllTrustingManager(), null);
		this.setSslCtx(context);
		WhanauKeyState.addSSLContextToStore(this.getPubKeyHash(), this.getSslCtx());
	}
	
	/**
	 * There is a static store that contains all of the SSL Contexts.
	 * This store is referenced by RMISSLClientSocketFactory
	 * 
	 * @param pubKeyHash 	String 		= the hash of the public key of the context to store 
	 * @param ctx			SSLContext	= the SSL context, from which socket factories are made
	 */
	private static void addSSLContextToStore (String pubKeyHash, SSLContext ctx) {
		if (WhanauKeyState.sslContextStore == null)
			WhanauKeyState.sslContextStore = new Hashtable<String, SSLContext>();
		synchronized(WhanauKeyState.sslContextStore) {
			WhanauKeyState.sslContextStore.put(pubKeyHash, ctx);
		}
	}
	
	/**
	 * Get the SSL Context that is bound to the given public key hash
	 * This is used by RMISSLClientSocketFactory to create sockets.
	 * The SSL context is already preloaded with the client keys for handshakes.
	 * Also, cache the most recent return value of this in 
	 * WhanauKeyState.mostRecentSSLContext
	 * 
	 * @param pubKeyHash 	String 		= public key of SSL context to retrieve 
	 * @return				SSLContext	= SSLContext, loaded with the proper keys
	 */
	public static SSLContext getSSLContext(String pubKeyHash) {
		synchronized(WhanauKeyState.sslContextStore) {
			if (WhanauKeyState.sslContextStore == null)
				return null;
			SSLContext result = WhanauKeyState.sslContextStore.get(pubKeyHash); 
			if (result != null)
				WhanauKeyState.mostRecentSSLContext = result;
			return result;
		}
	}
	
	/**
	 * Retrieve the most recent SSLContext that was retrieved from the context store.
	 * This is a gross hack to account for when remote instances of RMISSLClientSocketFactory
	 * are sent to us. We need to use our own keys to create sockets.
	 * 
	 * @return SSLContext = the most recent SSLContext used
	 */
	public static SSLContext getMostRecentSSLContext() {
		return WhanauKeyState.mostRecentSSLContext;
	}
	
	/**
	 * Return the hash of the public key
	 * 
	 * @return String = public key hash
	 */
	public synchronized String getPubKeyHash() {
		return this.pubKeyHash;
	}
	
	/**
	 * Set the public key hash
	 * 
	 * @param hash String = new public key hash
	 */
	private synchronized void setPubKeyHash(String hash) {
		this.pubKeyHash = hash;
	}
	
	/**
	 * Return the keys
	 * 
	 * @return KeyStore = keys
	 */
	public synchronized KeyStore getKeys() {
		return keys;
	}
	
	/**
	 * Set the keys
	 * 
	 * @param keys KeyStore = new keys
	 */
	private synchronized void setKeys(KeyStore keys) {
		this.keys = keys;
	}
	
	/**
	 * Return the key password
	 * 
	 * @return String = key password
	 */
	public synchronized String getKeyPassword() {
		return keyPassword;
	}
	
	/**
	 * Set the key password
	 * 
	 * @param keyPassword String = new key password
	 */
	private synchronized void setKeyPassword(String keyPassword) {
		this.keyPassword = keyPassword;
	}
	
	/**
	 * Get the SSL context
	 * 
	 * @return SSLContext = SSL context
	 */
	public synchronized SSLContext getSslCtx() {
		return sslCtx;
	}
	
	/**
	 * Set the SSL context
	 * @param sslCtx SSLContext = new SSL Context
	 */
	private synchronized void setSslCtx(SSLContext sslCtx) {
		this.sslCtx = sslCtx;
	}

}
