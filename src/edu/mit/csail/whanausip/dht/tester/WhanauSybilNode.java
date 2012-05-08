package edu.mit.csail.whanausip.dht.tester;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.security.KeyStore;
import java.util.LinkedList;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.threads.*;
import edu.mit.csail.whanausip.dht.*;
import edu.mit.csail.whanausip.dht.kvchecker.*;

/**
 * Blackhole Sybil Node
 * Template from WhanauVirtualNode
 * Responds to all requests for sampleNodes(...) with itself
 * Returns nothing for all else
 * 
 * @author ryscheng
 * @date 2010/08/10
 *
 */
public class WhanauSybilNode<T> implements MethodThreadInterface {
	private WhanauState<T> 			state;			//Stores the state of the node
	private SSLServerSocket			serverSock;		//socket to listen for incoming requests
	private MethodThreadBatchRun 	threadPool;		//Threadpool that serves each request	
	private Comparable<T> 			targetKey;	//Target key to cluster attack
	private boolean					isAlive;

	/**
	 * Initializes a new WhanauDHT sybil node with the following state.
	 * Target key specifies which key to key-cluster attack
	 * 
	 * @param state			WhanauState	= node state
	 * @param targetKey		String		= target attack key
	 * @throws RemoteException
	 */
	public WhanauSybilNode(WhanauState<T> state, Comparable<T> targetKey) throws IOException {
		this.state = state;
		this.threadPool = new MethodThreadBatchRun();
		this.targetKey = targetKey;
		this.isAlive = true;
		
		SSLServerSocket result = (SSLServerSocket) state.getSSLContext().getServerSocketFactory().createServerSocket(state.getLocalPort());
	    //Require Client Authentication
	    //result.setWantClientAuth(true);
	    //result.setNeedClientAuth(true);
	    this.serverSock = result;
	    //Start listening on the port
	    this.threadPool.addThread("listener", this, result);
		
		state.getLog().info("Initializing a new WhanauSybilNode on "+state.getHashHostPort());
	}
	
	/**
	 * Infinite loop grabs incoming sockets and starts a thread to serve it
	 * 
	 * @throws IOException
	 */
	public void startListening() throws IOException{
		while (this.isAlive) {
			Socket sock = this.serverSock.accept();
			this.threadPool.addThread(this.getState().getHashHostPort(),this,sock);
		}
	}
	
	/**
	 * Kills this node
	 * 
	 * @throws IOException
	 */
	public void kill() {
		this.isAlive = false;
		try {
			this.serverSock.close();
		} catch (IOException ex) {
			this.getState().getLog().warning("Error killing node at "+this.getState().getHashHostPort()+" : "+ex.getMessage());
		}
	}
	
	/**
	 * Returns the keys that this Sybil will target in a cluster ID attack
	 * 
	 * @return Comparable<T>
	 */
	public Comparable<T> getTargetKey() {
		return this.targetKey;
	}
	
	/**
	 * If the first parameter is an SSLServerSocket,
	 * it is a special thread, call startListening()
	 * Otherwise, handle incoming sockets to perform remote method calls.
	 * 
	 * @param parameters Object[] 	= SSLSocket or SSLServerSocket
	 * @return Object 				= return Object, null if fail
	 */
	public Object methodThread(Object[] parameters) {
		SSLSocket sock = null;
		try {
			//If SSLServerSocket, instruct to start listening
			if (parameters[0] instanceof SSLServerSocket) {
				this.startListening();
				return null;
			}
			//Grab the socket and look at the public key
			sock = (SSLSocket) parameters[0];
			String seeHash = CryptoTool.SHA1toHex(
	    			sock.getSession().getPeerCertificates()[0].getPublicKey().getEncoded());
			//Extract the method name and parameters
			ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
			String command = (String) in.readObject();
			Object[] param = (Object[]) in.readObject();
			Object result = null;
			
			//Call the respective method
			if (command.equals(WhanauDHTConstants.GETID_CMD)) {
				result = this.targetKey;
			} else if (command.equals(WhanauDHTConstants.GETPUBKEYHASH_CMD)) {
				result = this.getState().getPubKeyHash();
			} else if (command.equals(WhanauDHTConstants.SAMPLENODES_CMD)) {
				Integer numNodes = (Integer) param[0];
				LinkedList<Pair<Object,Long>> currList = new LinkedList<Pair<Object,Long>>();
				for (int i=0; i < numNodes; i++){
					currList.addLast(new Pair<Object,Long>(
								this.getState().getRandomMyRecord(),
								this.getState().generateQueryToken()));
				}
				result = currList;
			}
			//Return result and close
			ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());
			out.writeObject(result);
			sock.close();
			return result;
		} catch (Exception ex) {
			ex.printStackTrace();
			this.getState().getLog().warning("Error Processing Request: "+ex.getMessage());
		}
		try {
			if (sock != null)
				sock.close();
		} catch (IOException ex){
			this.getState().getLog().warning("Error closing socket");
		}
		return null;
	}
	
	/**
	 * Return the node's state
	 * 
	 * @return WhanauState<T>
	 */
	public synchronized WhanauState<T> getState() {
		return this.state;
	}
	
	/**
	 * Starts a single instance of WhanauSybilNode
	 * @param args - not used
	 */
	public static void main(String args[]) {
		String password = "password";
		String targetKeyFile = "lib/keys/1.jks";
		String sybilKeyFile = "lib/keys/9.jks";
		String hostname = "18.19.0.41";
		int port = 9010;
		try {
			Logger log = LogUtil.createLogger("sybil", null);
			KeyStore keys = CryptoTool.loadKeyStore(sybilKeyFile, password);
			WhanauKeyState keyState = new WhanauKeyState(keys, password);
			KeyValueChecker<String> kvChecker = new SigningKVChecker<String>(log,keyState,WhanauDHTConstants.DEFAULT_TTL);
			WhanauState<String> state = new WhanauState<String>("EVIL!", kvChecker,hostname,port,keyState,log,null);
			WhanauSybilNode<String> server = new WhanauSybilNode<String>(state,CryptoTool.getPublicKeyHashFromFile(targetKeyFile, password));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	} 

}
