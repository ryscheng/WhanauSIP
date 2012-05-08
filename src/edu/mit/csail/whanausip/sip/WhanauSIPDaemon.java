package edu.mit.csail.whanausip.sip;

import java.net.InetAddress;
import java.security.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.dht.*;
import edu.mit.csail.whanausip.dht.kvchecker.SigningKVChecker;
import edu.mit.csail.whanausip.dht.tester.WhanauSybilNode;


/**
 * Encloses WhanauSIP Gateway stack and WhanauDHT instance in one class
 *  
 * @author ryscheng
 * @date 2009/09/29
 */
public class WhanauSIPDaemon {
	private WhanauSIPGateway 			sipLayer;			//WhanauSIP gateway
	private WhanauVirtualNode<String> 	whanauNode;			//WhanauDHT node
	private Timer 						whanauSetupTimer;	//Timer to re-run setup
	
	/**
	 * Creates a WhanauDHT node, then adds a WhanauSIP gateway
	 * 
	 * @param host				String		= IP address or hostname of this machine
	 * @param whanauPort		int			= WhanauDHT listens on [port, port+2]
	 * @param sipPort			int			= port WhanauSIP gateway listens
	 * @param keys				KeyStore	= this node's public/private keys
	 * @param keyStorePassword	String		= password to unlock KeyStore
	 * @param controlKeys		Set<String>	= allowed keys to control this node
	 * 											using WhanauRefControl 
	 * @throws Exception = initialization fails
	 */
	public WhanauSIPDaemon(String host, int whanauPort, int sipPort, KeyStore keys, 
					String keyStorePassword, Set<String> controlKeys) throws Exception{
		
		//These properties were required of RMI
		/**
		//Set proper security and system properties
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
		System.setProperty("java.rmi.server.hostname", host);
		**/
		
		//Prepare published record
		//Create WhanauDHT node
		Logger log = LogUtil.createLogger("whanau"+whanauPort, "whanau"+whanauPort+".log");
		WhanauKeyState keyState = new WhanauKeyState(keys, keyStorePassword);
		String value = "sip:"+keyState.getPubKeyHash()+"@"+host+":"+sipPort;
		WhanauState<String> state = new WhanauState<String>(value, 
						new SigningKVChecker<String>(log,keyState,WhanauDHTConstants.DEFAULT_TTL), 
						host, whanauPort, keyState, log, "whanau"+whanauPort+".log");
		WhanauVirtualNode<String> server = new WhanauVirtualNode<String>(state, controlKeys);
		this.whanauNode = server;
		//Setup timer to re-run setup
		//Setup is run every SETUP_PERIOD milliseconds
		//WhanauSetupTimeTask.run() is run every SETUP_PERIOD/SETUP_TIMER_DIVIDER milliseconds
		//Check the method for what other tasks need to be run
		//this.whanauSetupTimer = new Timer(true);
        //this.whanauSetupTimer.scheduleAtFixedRate(new WhanauSetupTimerTask<String>(log, server), 
        //									new Date(System.currentTimeMillis() + SIPCommConstants.SETUP_PERIOD
        //												- (System.currentTimeMillis() % SIPCommConstants.SETUP_PERIOD)),
        //									SIPCommConstants.SETUP_PERIOD/SIPCommConstants.SETUP_TIMER_DIVIDER);
		//Initialize WhanauSIP gateway
		this.sipLayer = new WhanauSIPGateway(WhanauDHTConstants.DEFAULT_SIP_USERNAME,host,sipPort,server);
		this.sipLayer.setMessageProcessor(state);
	}
	
	/**
	 * Wrapper for sending SIP messages using the SIP stack in 
	 * WhanauSIPGateway
	 * 
	 * @param to		String = SIP URI of recipient
	 * @param message	String = message contents
	 * @throws Exception
	 */
	public void sendMessage(String to, String message) throws Exception{
		this.sipLayer.sendMessage(to, message);
	}
	
	/**
	 * Just starts this daemon with newly generated keys and default password
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String password = WhanauDHTConstants.DEFAULT_PASSWORD;
		int whanauPort = 9009;
		int sipPort = 9010;
		WhanauSIPDaemon daemon;
		String adminPubKeyHash = "9351356d8315469273b1d7ab701786c5db0e5ed1";
		HashSet<String> controlKeys = new HashSet<String>();
		controlKeys.add(adminPubKeyHash);
		try {
			/**@TODO is this localhost? hopefully not...**/
			String host = InetAddress.getLocalHost().getHostAddress().toString();
			
			System.out.println("Starting on "+host+":"+whanauPort+" with SIP listening on "+sipPort);
			daemon = new WhanauSIPDaemon(host, whanauPort, sipPort, CryptoTool.generateKeyStore(password), 
					password, controlKeys);
			System.out.println("Daemon Started");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
