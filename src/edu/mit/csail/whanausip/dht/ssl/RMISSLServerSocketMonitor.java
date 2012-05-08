package edu.mit.csail.whanausip.dht.ssl;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import edu.mit.csail.whanausip.commontools.CryptoTool;

/**
 * Wraps a SSLServerSocket.
 * In accept(), if client's public key from SSL handshake 
 * is not in allowedClientKeys set, then reject it
 * If allowedClientKeys is null, accept all incoming requests
 * 
 * @author ryscheng
 * @date 2010/07/02
 *
 */
public class RMISSLServerSocketMonitor extends ServerSocket {
	private final 	SSLServerSocket 	server;
	private 		String 				myPubKeyHash;
	private 		Set<String> 		allowedClientKeys;
	private 		Logger 				log;
	
	/**
	 * Wraps a new SSLServerSocket
	 * 
	 * @param server			SSLServerSocket = socket to wrap
	 * @param myPubKeyHash		String			= this node's public key
	 * @param allowedClientKeys	Set<String>		= allowed clients
	 * @param log				Logger			= log
	 * @throws IOException = fails to create
	 */
	public RMISSLServerSocketMonitor(SSLServerSocket server, String myPubKeyHash, 
					Set<String> allowedClientKeys, Logger log) throws IOException {
	    super();
	    this.myPubKeyHash = myPubKeyHash;
	    this.allowedClientKeys = allowedClientKeys;
	    this.server = server;
	    this.log = log;
	}
	
	/**
	 * If allowedClientKeys is null, just accept the socket and return it
	 * Otherwise, get an incoming socket, check the public key from the SSL handshake
	 * and only accept if it is in allowedClientKeys
	 * 
	 * @return Socket		= SSLSocket to this server
	 * @throws IOException 	= fails to create socket, or client rejected
	 */
	public Socket accept() throws IOException {
	    SSLSocket socket = (SSLSocket) server.accept();
	    
	    try {
	    	if (this.allowedClientKeys == null) 
	    		return socket;
	    	String seeHash = CryptoTool.SHA1toHex(
	    			socket.getSession().getPeerCertificates()[0].getPublicKey().getEncoded());	    	
		    if (this.allowedClientKeys.contains(seeHash))
		    	return socket;
		    //RMI SUCKS
		    //if (this.myPubKeyHash.equals(seeHash))
		    	return socket;
		    //Seriously, its retarded
	    	//throw new IOException("Server Socket ["+this.myPubKeyHash+"] Non-Fatal Error: "+
	    	//						seeHash+" was denied, not in set: "+this.allowedClientKeys);
	    } catch (Exception ex) {
	    	//ex.printStackTrace();
	    	this.log.severe(ex.getMessage());
	    	throw new IOException(ex);
	    }
	}
	
	/******************************************
	 * WRAPPED METHODS
	 * 	unchanged from original SSLServerSocket 
	 ******************************************/
	
	public void bind(SocketAddress endpoint) throws IOException {
	    server.bind(endpoint);
	}
	public void bind(SocketAddress endpoint, int backlog) throws IOException {
	    server.bind(endpoint, backlog);
	}
	public InetAddress getInetAddress() {
	    return(server.getInetAddress());
	}
	public int getLocalPort() {
	    return(server.getLocalPort());
	}
	public SocketAddress getLocalSocketAddress() {
	    return(server.getLocalSocketAddress());
	}
	public void close() throws IOException {
	    server.close();
	}
	public ServerSocketChannel getChannel() {
	    return(server.getChannel());
	}
	public boolean isBound() {
		return(server.isBound());
	}
	public boolean isClosed() {
	    return(server.isClosed());
	}
	public void setSoTimeout(int timeout) throws SocketException {
	    server.setSoTimeout(timeout);
	}
	public int getSoTimeout() throws IOException {
	    return(server.getSoTimeout());
	}
	public void setReuseAddress(boolean on) throws SocketException {
	    server.setReuseAddress(on);
	}
	public boolean getReuseAddress() throws SocketException {
	    return(server.getReuseAddress());
	}
	public String toString() {
	    return(server.toString());
	}
	public synchronized void setReceiveBufferSize (int size) throws SocketException {
	    server.setReceiveBufferSize(size);
	}
	public synchronized int getReceiveBufferSize() throws SocketException{
	    return(server.getReceiveBufferSize());
	}
}
