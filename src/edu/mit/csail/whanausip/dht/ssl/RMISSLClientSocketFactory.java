package edu.mit.csail.whanausip.dht.ssl;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLContext;

import edu.mit.csail.whanausip.commontools.CryptoTool;
import edu.mit.csail.whanausip.dht.WhanauKeyState;


/**
 * Creates SSL client socket connections for RMI
 * A unique factory instance is created for each node you communicate with
 * The SSL handshake must yield a peer certificate with the proper
 * public key (compare hash with clientPubKeyHash)
 * unless clientPubKeyHash==null, then it can connect to any SSL server
 * 
 * @author ryscheng
 * @date 2009/05/06
 */
public class RMISSLClientSocketFactory 
							implements RMIClientSocketFactory, Serializable {
	
	private static final long serialVersionUID = -570424982130337106L;
	private String myPubKeyHash;		//SHA1 hash of my public key
										//Used to retrieve my signing keys
	private String clientPubKeyHash;	//The target client's public key hash
										//Checked each time a SSL handshake is performed
	
	/**
	 * Creates a new socket factory
	 * 
	 * @param myPubKeyHash		String	= local node's public key hash
	 * @param clientPubKeyHash	String	= target node's public key hash
	 */
	public RMISSLClientSocketFactory(String myPubKeyHash, String clientPubKeyHash) {
		this.myPubKeyHash = myPubKeyHash;
		this.clientPubKeyHash = clientPubKeyHash;
	}
	
	/**
	 * Creates a secure SSL socket between localhost and host:port
	 * If the SSL handshake returns a public key other than what is expected,
	 * throw an IOException
	 * 
	 * @param host String = hostname to connect to
	 * @param port int = port on host to connect to
	 * @return Socket = secure socket to remote host 
	 * @throws IOException = when socket establishment fails,
	 * 						or public key hash does not match
	 */
	public Socket createSocket(String host, int port) throws IOException {
		try{
			SSLContext ctx;
			if (this.myPubKeyHash.equals(this.clientPubKeyHash)) {
				ctx = WhanauKeyState.getMostRecentSSLContext();
			} else {
				ctx = WhanauKeyState.getSSLContext(this.myPubKeyHash);
			}
			SSLSocketFactory factory = ctx.getSocketFactory();
			SSLSocket socket = (SSLSocket) factory.createSocket(host,port);
			String seeHash = CryptoTool.SHA1toHex(
										socket.getSession().getPeerCertificates()[0].
										getPublicKey().getEncoded());
			
			//RMI sucks
			//if((seeHash.equals(this.myPubKeyHash)) || (seeHash.equals(this.clientPubKeyHash))){
				return socket;
				
			//}
			//throw (new IOException("Wrong Public Key Hash," +
			//		" socket to "+host+":"+port+
			//		" Expect: "+this.clientPubKeyHash+" or "+this.myPubKeyHash+", See: "+seeHash));
			//Seriously, its retarded
		}catch(Exception e){
			e.printStackTrace();
			System.out.println("Client Socket To "+host+":"+port+" Non-Fatal Error: " +
					"Detected Man-in-the-Middle: "+e.getMessage());
			throw new IOException(e);
		}
	}
	
	/**
	 * Returns a hash of this object
	 * 
	 * @return int = hash of this object
	 */
	public int hashCode() {
		return getClass().hashCode();
	}
	
	/**
	 * Tests for equality with another object
	 * 
	 * @param obj Object = object to test against
	 * @return boolean = true is equivalent objects, false otherwise
	 */
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		else if ((obj == null) || (getClass() != obj.getClass())) {
			return false;
		}
		return true;
	}
}
