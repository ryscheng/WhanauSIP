package edu.mit.csail.whanausip.dht;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.*;
import edu.mit.csail.whanausip.commontools.threads.*;

/**
 * Stores a single peer in social network of WhanauDHT
 * 
 * @author ryscheng
 * @date 2009/03/01
 */
public class WhanauRPCClientStub<T> {
	private Logger 		log;				//Logger
	private String 		hostname;			//Peer's hostname or IP
	private int 		port;				//Peer's public reference port
	private String 		pubKeyHash;			//Peer's public key hash
	private SSLContext	sslCtx;				//Client socket factory
	private boolean		activeStatus;		//true if SSL sockets can be made,
											//false if host is not at hostname:port
	
	/**
	 * Stores info for a peer
	 * No network connection is necessary to run
	 * This should be used for actual neighbors connected by social links
	 * 
	 * @param log			Logger		= log entries to this Logger
	 * @param mySSLCtx		SSLContext	= local node's SSL Context
	 * @param pubKeyHash 	String 		= hash of peer's public key
	 * @param host 			String		= peer's hostname or IP
	 * @param port 			int			= peer's public reference port
	 */
	public WhanauRPCClientStub(Logger log, SSLContext mySSLCtx ,String pubKeyHash, String host, int port) {
		this.log = log;
		this.pubKeyHash=pubKeyHash;
		this.hostname = host;
		this.port = port;
		this.sslCtx = mySSLCtx;
		this.activeStatus = true;
	}

	/**
	 * Return this peer's public key hash
	 * 
	 * @return String = public key hash
	 */
	public synchronized String getPubKeyHash() {
		return this.pubKeyHash;
	}
	
	/**
	 * Return this peer's hostname
	 * 
	 * @return String = hostname
	 */
	public synchronized String getHostname() {
		return hostname;
	}
	
	/**
	 * Returns this peer's port
	 * 
	 * @return int = port
	 */
	public synchronized int getPort() {
		return port;
	}

	/**
	 * Returns the public key hash, hostname, and port
	 * in a single String
	 * 
	 * @return String
	 */
	public synchronized String getHashHostPort(){
		return (this.getPubKeyHash()+":"+this.getHostname()+":"+this.getPort());
	}
	
	/**
	 * Returns the local node's SSL Context
	 * 
	 * @return SSLContext
	 */
	public synchronized SSLContext getSSLCtx() {
		return this.sslCtx;
	}
	
	/**
	 * Sets the current active status
	 * @param status boolean
	 */
	public synchronized void setActiveStatus(boolean status) {
		this.activeStatus = status;
	}
	
	/**
	 * Checks to see if this peer is active
	 * If we checked recently, return cached value
	 * 
	 * @return boolean = peer is active
	 */
	public boolean isActive() {
		return this.activeStatus;
	}
	
	/**
	 * Creates a new SSLSocket to this node.
	 * Also checks that it has the proper keys, otherwise throws
	 * UnknownHostException for man-in-the-middle
	 * 
	 * @return SSLSocket = new secure socket
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	private SSLSocket createSocket() throws UnknownHostException, IOException, NoSuchAlgorithmException {
		SSLSocketFactory factory = this.getSSLCtx().getSocketFactory();
		SSLSocket socket = (SSLSocket) factory.createSocket(this.getHostname(), this.getPort());
		String seeHash = CryptoTool.SHA1toHex(
				socket.getSession().getPeerCertificates()[0].
				getPublicKey().getEncoded());
		if (this.getPubKeyHash()!=null && !seeHash.equals(this.getPubKeyHash())) {
			throw new UnknownHostException("Detected man-in-the-middle! Expect="+this.getPubKeyHash()+", See="+seeHash);
		}
		return socket;
	}
	
	/**
	 * Calls a remote method
	 * Marshalls entire Java Objects using the builtin ObjectOutputStream.
	 * Notice that each method call requires its own SSLSocket.
	 * ObjectInputStream requires that the entire stream is read to EOF before it will 
	 * convert what it read into Objects.
	 * Inefficient, but working
	 * 
	 * @param command 	String 		= name of method
	 * @param param 	Object[] 	= parameters to remote method
	 * @return Object				= return Object
	 */
	public Object remoteCall(String command, Object... param) throws Exception{
		Object result = null;
		SSLSocket sock = null;
		try {
			sock = this.createSocket();
			ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
			out.writeObject(command);
			out.writeObject(param);
			
			ObjectInputStream in = new ObjectInputStream(sock.getInputStream());			
			result = in.readObject();
			sock.close();
		} catch (Exception ex) {
			this.setActiveStatus(false);
			this.log.fine(this.getHashHostPort()+" failed:"+ex.getMessage());
			if (sock != null)
				sock.close();
			throw ex;
		}
		this.log.fine(this.getHashHostPort()+"."+command+"(...) complete");
		this.setActiveStatus(true);
		return result;
	}
	
	
	
	public static void main(String args[]) {
		String password = "password";
		String keyFile = "lib/keys/1.jks";
		String serverKeyFile = "lib/keys/0.jks";
		String host = "localhost";
		int port = 9009;		
		try {
			String serverPubKeyHash = CryptoTool.getPublicKeyHashFromFile(serverKeyFile, password);
			Logger log = LogUtil.createLogger("whanau", null);
			KeyStore keys = CryptoTool.loadKeyStore(keyFile, password); 
			WhanauKeyState keyState = new WhanauKeyState(keys, password);
			WhanauRPCClientStub<String> peer = new WhanauRPCClientStub<String>(log, keyState.getSslCtx(), serverPubKeyHash, host, port);
			Object result = peer.remoteCall("getStateStr", (Object[])null);
			System.out.println(result);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
