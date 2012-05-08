/**
 * Creates SSL server socket connections for RMI
 */
package edu.mit.csail.whanausip.dht.ssl;

import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;

import edu.mit.csail.whanausip.dht.WhanauKeyState;


/**
 * Creates SSL server socket connections for RMI
 * All server sockets are wrapped in a monitor to check client connections
 * Only clients with public keys in allowedClientKeys are allowed to connec to the socket
 * 
 * @author ryscheng
 * @date 2009/05/06
 */
public class RMISSLServerSocketFactory implements RMIServerSocketFactory {
	
	private SSLServerSocketFactory 	ssf;				//Socket Factory
	private String 					myPubKeyHash;		//This node's pubKeyHash
	private Set<String> 			allowedClientKeys;	//Allowed clients
	private Logger 					log;				//Logger
	
	
	/**
	 * Creates a new server socket factory
	 * 
	 * @param myPubKeyHash		String		= This node's pubKeyHash
	 * @param allowedClientKeys	Set<String>	= Allowed clients' pubKeyHashes
	 * @param log				Logger		= Logger
	 */
	public RMISSLServerSocketFactory(String myPubKeyHash, 
										Set<String> allowedClientKeys, Logger log){
		SSLContext ctx = WhanauKeyState.getSSLContext(myPubKeyHash);
		this.ssf = ctx.getServerSocketFactory();
		this.myPubKeyHash = myPubKeyHash;
		this.allowedClientKeys = allowedClientKeys;
		this.log = log;
	}
	
	/**
	 * Creates a secure SSL server socket on given port
	 * All sockets are wrapper in a monitor to check client's key
	 * 
	 * @param port 	int 	= port to create socket on
	 * @return 		Socket 	= server socket
	 * @throws IOException 	= when socket establishment fails,
	 * 							or client is rejected
	 */
	public ServerSocket createServerSocket(int port) throws IOException {
	    SSLServerSocket result = (SSLServerSocket) ssf.createServerSocket(port);
	    //Require Client Authentication
	    result.setWantClientAuth(true);
	    result.setNeedClientAuth(true);
		return (new RMISSLServerSocketMonitor(result, this.myPubKeyHash, 
					this.allowedClientKeys, this.log));
    }

	/**
	 * Returns a hash of this object
	 * @return int = hash of this object
	 */
    public int hashCode() {
    	return getClass().hashCode();
    }

    /**
	 * Tests for equality with another object
	 * @param obj Object = object to test against
	 * @return boolean = true is equivalent objects, false otherwise
	 */
    public boolean equals(Object obj) {
		if (obj == this) {
		    return true;
		} else if (obj == null || getClass() != obj.getClass()) {
		    return false;
		}
		return true;
    }


}
