package edu.mit.csail.whanausip.dht.tester;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.Set;

import edu.mit.csail.whanausip.dht.WhanauKeyState;
import edu.mit.csail.whanausip.dht.WhanauRPCClientStub;
import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.commontools.threads.MethodThreadInterface;

/**
 * Stores a list of remote references to Whanau DHT nodes
 * Used for PlanetLab testing
 * 
 * @author ryscheng
 * @date 2010/01/19 
 */
public class WhanauCommandClientList<T> implements MethodThreadInterface{
	/**
	 * key=hostname, value=remoteReference container
	 */
	private ConcurrentHashMap<String, WhanauRPCClientStub<T>> 	clients;
	private WhanauKeyState										keyState;
	private Random 												randomGen;
	private Logger 												log;
	
	/**
	 * Initializes a new list of RPC clients
	 * Each node can run multiple instances, so we 
	 * insert unique RPC clients for each node from 
	 * [startPort, startPort+numPorts)
	 * 
	 * @param keys			KeyStore= keys to use by this master
	 * @param password		String 	= keystore password for keys 
	 * @param nodeListFile	String	= file of nodes to connect to
	 * @param startPort		String	= destination port for each
	 * @param numPorts		String	= number of ports starting from
	 * 									startPort on each dst
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws UnrecoverableKeyException
	 * @throws KeyManagementException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 */
	public WhanauCommandClientList(KeyStore keys, String password, String nodeListFile,	int startPort, int numPorts) 
												throws FileNotFoundException, IOException, 
												UnrecoverableKeyException, KeyManagementException, 
												NoSuchAlgorithmException, KeyStoreException{
		this.clients = new ConcurrentHashMap<String, WhanauRPCClientStub<T>>();
		this.keyState = new WhanauKeyState(keys, password);
		this.randomGen = new Random();
		this.log = Logger.getLogger("WhanauCommandClientList");
		this.initialize(nodeListFile, startPort, numPorts);
	}
	
	/**
	 * Returns the logger
	 * 
	 * @return Logger
	 */
	private Logger getLog() {
		return this.log;
	}
	
	/**
	 * Returns a particular client
	 * 
	 * @param key String = hostname
	 * @return
	 */
	public WhanauRPCClientStub<T> getClient(String key) {
		return this.clients.get(key);
	}
	
	/**
	 * Initialize the list by reading the file of nodes
	 * Each node can run multiple instances, so we 
	 * insert unique RPC clients for each node from 
	 * [startPort, startPort+numPorts)
	 * 
	 * @param nodeListFile 	String 	= file of nodes to connect to
	 * @param startPort		int		= starting port
	 * @param numPorts		int		= number of instances per node
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void initialize(String nodeListFile, int startPort, int numPorts) 
										throws FileNotFoundException, IOException {
		FileReader nodeFile = new FileReader(nodeListFile);
		BufferedReader bufRead = new BufferedReader(nodeFile);
		String line = bufRead.readLine();
		
		while (line != null){
			line = line.trim();
			for (int i=0;i<numPorts;i++) {
				this.clients.put(line+":"+(startPort+i), 
							new WhanauRPCClientStub<T>(this.getLog(), this.keyState.getSslCtx(), 
														null, line, (startPort+i)));
			}
			line = bufRead.readLine();
		}
		bufRead.close();
	}
	
	/**
	 * Used to parallelize remote calls
	 * 
	 * @param 	Object[0] String 	= key in Hashtable
	 * 			Object[1] String 	= command to remotely call
	 * 			Object[2] Object[] 	= parameters to pass to remote method
	 */
	public Object methodThread(Object[] args) {
		Object result=null;
		try {
			WhanauRPCClientStub<T> node;
			String name = (String) args[0];
			String command = (String) args[1];
			Object[] param = null;
			if (args.length > 2)
				param = (Object[]) args[2];
			
			if (this.clients.containsKey(name)) {
				node = this.getClient(name);
				result = node.remoteCall(command, param);
			} else {
				System.err.println("WhanauCommandClientList: "+name+"."+command+"() failed, node does not exist");
			}
		} catch (Exception e) {
			//e.printStackTrace();
			System.err.println("WhanauCommandClientList: methodThread() failed: " + e.getMessage());
		}
		
		return result;
	}
	
	/**
	 * Returns all of the keys in the Hashtable
	 * 
	 * @return Set<String>
	 */
	public Set<String> getAllNames() {
		return this.clients.keySet();
	}
	
	/**
	 * Returns a random key in the Hashtable
	 * 
	 * @return String
	 */
	public String getRandomKey(){
		Set<String> keySet = this.getAllNames();
		String[] keys = keySet.toArray(new String[0]);
		if(keys.length<=0) {
			return null;
		}
		return keys[this.randomGen.nextInt(keys.length)];
	}

}
