package edu.mit.csail.whanausip.dht;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.RemoteException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.remote.*;
import edu.mit.csail.whanausip.commontools.threads.*;
import edu.mit.csail.whanausip.dht.kvchecker.KeyValueChecker;
import edu.mit.csail.whanausip.dht.kvchecker.SigningKVChecker;

/**
 * Initializes a new WhanauDHT node.
 * Listens on a port for SSL connections and answers requests
 * 
 * @author ryscheng
 * @date 2010/07/06
 */
public class WhanauVirtualNode<T> implements MethodThreadInterface {
	private WhanauState<T> 			state;			//Stores the state of the node
	private WhanauRefPublicImpl<T> 	publicRef;		//local public reference
	private WhanauRefPeerImpl<T> 	peerRef;		//local peer reference
	private WhanauRefControlImpl<T> controlRef;		//local control reference
	private SSLServerSocket			serverSock;		//socket to listen for incoming requests
	private MethodThreadBatchRun 	threadPool;		//Threadpool that serves each request	
	private Set<String> 			controlKeys;	//Keys that can control this node
	private boolean					isAlive;

	/**
	 * Initializes a new WhanauDHT node with the following state.
	 * The controlKeys defines who is authorized to remotely control this node
	 * If it is null, anyone can, if it is an empty set, noone can
	 * 
	 * @param state			WhanauState	= node state
	 * @param controlKeys	Set<String>	= control keys
	 * @throws RemoteException
	 */
	public WhanauVirtualNode(WhanauState<T> state, Set<String> controlKeys) throws IOException {
		this.state = state;
		this.publicRef = new WhanauRefPublicImpl<T>(state);
		this.peerRef = new WhanauRefPeerImpl<T>(state);
		this.controlRef = new WhanauRefControlImpl<T>(state, controlKeys);
		this.threadPool = new MethodThreadBatchRun();
		this.controlKeys = controlKeys;
		this.isAlive = true;
		
		SSLServerSocket result = (SSLServerSocket) state.getSSLContext().getServerSocketFactory().createServerSocket(state.getLocalPort());
	    //Require Client Authentication
	    result.setWantClientAuth(true);
	    result.setNeedClientAuth(true);
	    this.serverSock = result;
	    //Start listening on the port
	    this.threadPool.addThread("listener", this, result);
		
		state.getLog().info("Initializing a new WhanauVirtualNode on "+state.getHashHostPort());
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
	 * Kills this node and all children nodes
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
		for (WhanauVirtualNode<T> n : this.controlRef.getChildNodes()) {
			n.kill();
		}
	}
	
	/**
	 * Returns the keys that can control this node
	 * 
	 * @return Set<String>
	 */
	public Set<String> getControlKeys() {
		return this.controlKeys;
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
				result = this.getPublicRef().getID((Long) param[0], (Integer) param[1]);
			} else if (command.equals(WhanauDHTConstants.SUCCESSORSSAMPLE_CMD)) {
				result = this.getPublicRef().successorsSample((Long)param[0], (Comparable<T>)param[1]);
			} else if (command.equals(WhanauDHTConstants.LOOKUPTRY_CMD)) {
			 	result = this.getPublicRef().lookupTry((Long)param[0], (Integer)param[1], (Comparable<T>)param[2]);
			} else if (command.equals(WhanauDHTConstants.GETPUBKEYHASH_CMD)) {
				result = this.getPublicRef().getPubKeyHash();
			} else if (command.equals(WhanauDHTConstants.WAITSTAGE_CMD)) {
				result = this.getPublicRef().waitStage((Integer)param[0]);
			} else if (command.equals(WhanauDHTConstants.QUERY_CMD)) {
				result = this.getPublicRef().query((Comparable<T>)param[0], (Integer)param[1]);
			} else if (command.equals(WhanauDHTConstants.SAMPLENODES_CMD)) {
				//Check permissions
				Integer numNodes = (Integer) param[0];
				Integer steps = (Integer) param[1];
				if (this.getState().getPeerByPubKeyHash(seeHash) == null) {
					throw new Exception("UNAUTHORIZED ATTEMPT to sampleNodes() from "+seeHash+
							" not in set "+this.getState().getAllPeerKeys());
				}
				//Wait until other nodes have caught up
				this.getState().waitForSetupStage(WhanauState.STAGE1);
				//Tally total calls to sampleNodes and reject if over limit
				this.getState().addPeerRandWalkCount(seeHash, (numNodes*steps));
				if (this.getState().getPeerRandWalkCount(seeHash) > WhanauDHTConstants.MAX_RANDWALK_NUMNODESxSTEPS) {
					throw new Exception("Peer "+seeHash+" exceeded maximum randwalkcount: "+
											this.getState().getPeerRandWalkCount(seeHash));
				}
				result = this.getPeerRef().sampleNodes(numNodes, steps); 
			} else {  //CONTROL METHODS
				//Check permissions
				if (this.controlKeys==null || !this.getControlKeys().contains(seeHash)) {
					throw new Exception("UNAUTHORIZED ATTEMPT to "+command+"() from "+seeHash+
							" not in set "+this.getControlKeys());
				}
				
				if (command.equals(WhanauDHTConstants.CREATENODE_CMD)) {
					result = this.getControlRef().createNode((Serializable)param[0], (KeyValueChecker<T>)param[1],(String)param[2],(Integer)param[3],(KeyStore)param[4],(String)param[5]);
				} else if (command.equals(WhanauDHTConstants.GETSTATE_CMD)) {
					result = this.getControlRef().getState();
				} else if (command.equals(WhanauDHTConstants.GETSTATESTR_CMD)) {
					result = this.getControlRef().getStateStr();
				} else if (command.equals(WhanauDHTConstants.GETLOGSTR_CMD)) {
					result = this.getControlRef().getLogStr();
				} else if (command.equals(WhanauDHTConstants.ADDPEER_CMD)) {
					result = this.getControlRef().addPeer((String)param[0],(String)param[1],(Integer)param[2]);
				} else if (command.equals(WhanauDHTConstants.REMOVEALLPEERS_CMD)) {
					result = this.getControlRef().removeAllPeers();
				} else if (command.equals(WhanauDHTConstants.SETSETUPSTAGE_CMD)) {
					result = this.getControlRef().setSetupStage((Integer)param[0]);
				} else if (command.equals(WhanauDHTConstants.RUNSETUPPART1THREAD_CMD)) {
					result = this.getControlRef().runSetupPart1Thread((Integer)param[0],(Integer)param[1],(Integer)param[2],(Integer)param[3]);
				} else if (command.equals(WhanauDHTConstants.RUNSETUPPART2THREAD_CMD)) {
					result = this.getControlRef().runSetupPart2Thread((Integer)param[0],(Integer)param[1],(Integer)param[2],(Integer)param[3]);
				} else if (command.equals(WhanauDHTConstants.JOINSETUPTHREAD_CMD)) {
					result = this.getControlRef().joinSetupThread();
				} else if (command.equals(WhanauDHTConstants.GETSETUPTHREADRESULT_CMD)) {
					result = this.getControlRef().getSetupThreadResult();
				} else if (command.equals(WhanauDHTConstants.LOOKUP_CMD)) {
					result = this.getControlRef().lookup((Integer)param[0],(Integer)param[1],(Integer)param[2],(Integer)param[3],(Comparable<T>)param[4]);
				} else if (command.equals(WhanauDHTConstants.PUBLISHVALUE_CMD)) {
					result = this.getControlRef().publishValue((Serializable)param[0]);
				} 
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
	 * Return the public reference
	 * 
	 * @return WhanauRefPublic<T>
	 */
	public synchronized WhanauRefPublic<T> getPublicRef() {
		return this.publicRef;
	}
	
	/**
	 * Return the peer reference
	 * 
	 * @return WhanauRefPeer<T>
	 */
	public synchronized WhanauRefPeer<T> getPeerRef() {
		return this.peerRef;
	}
	
	/**
	 * Return the control reference
	 * 
	 * @return WhanauRefControl<T>
	 */
	public synchronized WhanauRefControl<T> getControlRef() {
		return this.controlRef;
	}

	/**
	 * Starts a single instance of WhanauVirtualNode
	 * @param args - not used
	 */
	public static void main(String args[]) {
		String password = "password";
		String hostname = "18.26.4.169";
		String value = "sip:whanausip@"+hostname;
		String clientKeyFile = "lib/keys/0.jks";
		String myKeyFile = "lib/keys/1.jks";
		int port = 9009;
		HashSet<String> controlKeys = new HashSet<String>();
		try {
			controlKeys.add(CryptoTool.getPublicKeyHashFromFile(clientKeyFile, password));
			Logger log = LogUtil.createLogger("whanau", null);
			KeyStore keys = CryptoTool.loadKeyStore(myKeyFile, password); 
			WhanauKeyState keyState = new WhanauKeyState(keys, password);
			KeyValueChecker<String> kvChecker = new SigningKVChecker<String>(log,keyState,WhanauDHTConstants.DEFAULT_TTL);
			WhanauState<String> state = new WhanauState<String>(value, kvChecker,hostname,port,keyState,log,null);
			WhanauVirtualNode<String> server = new WhanauVirtualNode<String>(state,controlKeys);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
	} 
}
