package edu.mit.csail.whanausip.sip;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TooManyListenersException;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import edu.mit.csail.whanausip.dht.WhanauVirtualNode;
import edu.mit.csail.whanausip.commontools.WhanauDHTConstants;
import edu.mit.csail.whanausip.commontools.SIPCommConstants;

/**
 * Adapted from JAIN SIP example: (2007/10/17)
 * http://www.oracle.com/technology/pub/articles/dev2arch/2007/10/introduction-jain-sip.html
 * 
 * Creates a WhanauSIP gateway that responds to all messages.
 * It replies to all SIP messages with a REDIRECT response.
 * For example: 
 *     - MESSAGE to sip:123456789@localhost
 *     - perform a lookup of '123456789' on WhanauDHT yields 'IPADDRESS'
 *     - reply with REDIRECT to sip:123456789@IPADDRESS
 * For the time being, it will log all MESSAGE requests
 * 
 * @author ryscheng
 * @date 2010/07/15
 */
public class WhanauSIPGateway implements SipListener {

    private SIPMessageProcessor 	messageProcessor;	//Processes all log and messages
    private String 					username;			//My username
    private SipStack 				sipStack;			//SIP stack
    private SipFactory 				sipFactory;			//Creates new SIP elements
    private AddressFactory 			addressFactory;		//Creates new SIP addresses
    private HeaderFactory 			headerFactory;		//Creates new SIP headers
    private MessageFactory 			messageFactory;		//Creates new SIP messages
    private SipProvider 			sipProvider;		//SIP provider
    private WhanauVirtualNode<String>whanauNode;		//Whanau Virtual Node

    /**
     * Creates a new WhanauSIP Gateway
     * 
     * @param username	String	= this gateway's username
     * @param ip		String	= this gateway's IP address
     * @param port		int		= port this gateway listens on
     * @param whanauNode WhanauVirtualNode<String> = node to perform lookups on
     * @throws PeerUnavailableException
     * @throws TransportNotSupportedException
     * @throws InvalidArgumentException
     * @throws ObjectInUseException
     * @throws TooManyListenersException
     */
    public WhanauSIPGateway(String username, String ip, int port, WhanauVirtualNode<String> whanauNode)
									    throws PeerUnavailableException, TransportNotSupportedException,
									    InvalidArgumentException, ObjectInUseException,
									    TooManyListenersException {
    	this.whanauNode = whanauNode;
		setUsername(username);
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "WhanauSIP"+port);
		//properties.setProperty("javax.sip.IP_ADDRESS", ip);
	
		//DEBUGGING:
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
			"sip"+port+".log");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
			"sipdebug"+port+".log");
	
		sipStack = sipFactory.createSipStack(properties);
		headerFactory = sipFactory.createHeaderFactory();
		addressFactory = sipFactory.createAddressFactory();
		messageFactory = sipFactory.createMessageFactory();
	
		ListeningPoint tcp = sipStack.createListeningPoint(ip,port, "tcp");
		ListeningPoint udp = sipStack.createListeningPoint(ip,port, "udp");
	
		sipProvider = sipStack.createSipProvider(tcp);
		sipProvider.addSipListener(this);
		sipProvider = sipStack.createSipProvider(udp);
		sipProvider.addSipListener(this);
	}
	
    /**
	 * This method uses the SIP stack to send a message.
	 * 
	 * @param to 		String = SIP URI of recipient
	 * @param message 	String = message contents to send
	 */
	public void sendMessage(String to, String message) throws ParseException,
		    InvalidArgumentException, SipException {
	
		SipURI from = addressFactory.createSipURI(getUsername(), getHost()
			+ ":" + getPort());
		Address fromNameAddress = addressFactory.createAddress(from);
		fromNameAddress.setDisplayName(getUsername());
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress,
			"whanausipv1.0");
	
		String username = to.substring(to.indexOf(":") + 1, to.indexOf("@"));
		String address = to.substring(to.indexOf("@") + 1);
	
		SipURI toAddress = addressFactory.createSipURI(username, address);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(username);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);
	
		SipURI requestURI = addressFactory.createSipURI(username, address);
		requestURI.setTransportParam("udp");
	
		ArrayList viaHeaders = new ArrayList();
		ViaHeader viaHeader = headerFactory.createViaHeader(getHost(),
			getPort(), "udp", "branch1");
		viaHeaders.add(viaHeader);
	
		CallIdHeader callIdHeader = sipProvider.getNewCallId();
	
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1,
			Request.MESSAGE);
	
		MaxForwardsHeader maxForwards = headerFactory
			.createMaxForwardsHeader(70);
	
		Request request = messageFactory.createRequest(requestURI,
			Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
			toHeader, viaHeaders, maxForwards);
	
		SipURI contactURI = addressFactory.createSipURI(getUsername(),
			getHost());
		contactURI.setPort(getPort());
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(getUsername());
		ContactHeader contactHeader = headerFactory
			.createContactHeader(contactAddress);
		request.addHeader(contactHeader);
	
		ContentTypeHeader contentTypeHeader = headerFactory
			.createContentTypeHeader("text", "plain");
		request.setContent(message, contentTypeHeader);
	
		sipProvider.sendRequest(request);
    }

    /** 
     * This method is called by the SIP stack when a response arrives.
     * 
     *  @param evt ResponseEvent = Response from previously sent request
     **/
    public void processResponse(ResponseEvent evt) {
		Response response = evt.getResponse();
		int status = response.getStatusCode();
	
		if ((status >= 200) && (status < 300)) { //Success!
		    messageProcessor.processInfo("IMSIPLayer.processResponse(..) -- Success!");
		    return;
		}
		messageProcessor.processError("IMSIPLayer.processResponse(..) - Previous message not sent: " + status);
    }

    /** 
     * This method is called by the SIP stack when a new request arrives.
     * This needs to extract the pubKeyHash, perform a lookup
     * and issue a REDIRECT response
     * 
     * @param evt RequestEvent = incoming SIP request
     */
    public void processRequest(RequestEvent evt) {
		Request req = evt.getRequest();
		String method = req.getMethod();
		FromHeader from = (FromHeader) req.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) req.getHeader(ToHeader.NAME);
		Response response = null;
		
		//Log MESSAGEs and accept CANCEL
		if (method.equals("MESSAGE")) {
			messageProcessor.processMessage(from.getAddress().toString(),new String(req.getRawContent()));
		} else if (method.equals("CANCEL")) {
			try { //Reply with OK
				response = messageFactory.createResponse(200, req);
				ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
				toHeader.setTag("888"); //This is mandatory as per the spec.
				ServerTransaction st = sipProvider.getNewServerTransaction(req);
				st.sendResponse(response);
			} catch (Throwable e) {
				e.printStackTrace();
				messageProcessor.processError("IMSIPLayer.processRequest(..) - Failed sending OK reply to CANCEL request: "+e.getMessage());
			}
			return;
		}
		
		
		try {
			//Parse address and extract username = pubKeyHash to lookup
			String toLookup = ((SipURI)to.getAddress().getURI()).getUser();
			//Perform lookup
			Object dhtRecord = this.whanauNode.getControlRef().lookup(WhanauDHTConstants.LOOKUP_TIMEOUT, 
					WhanauDHTConstants.QUERY_TIMEOUT, SIPCommConstants.NUM_LOOKUP_THREADS, WhanauDHTConstants.W, toLookup);
			String result = (String) this.whanauNode.getState().getKVChecker().getValueFromRecord(dhtRecord);
			//Craft REDIRECT message
			response = messageFactory.createResponse(302, req); //302 = Redirect Moved Temporarily
			ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
			toHeader.setTag("888"); //This is mandatory as per the spec.
			//SipURI newURI = addressFactory.createSipURI(toLookup, newHost);
			Address newAddress = addressFactory.createAddress(result);
			ContactHeader contactHdr = headerFactory.createContactHeader(newAddress);
			response.addHeader(contactHdr);
			ServerTransaction st = sipProvider.getNewServerTransaction(req);
			st.sendResponse(response);
		} catch (Throwable e) {
			e.printStackTrace();
			messageProcessor.processError("IMSIPLayer.processRequest(..) - Failed sending redirect: "+e.getMessage());
		}
	    return;
    }

    /** 
     * This method is called by the SIP stack when there's no answer 
     * to a message. Note that this is treated differently from an error
     * message. 
     * 
     * @param evt TimeoutEvent = timeout event
     */
    public void processTimeout(TimeoutEvent evt) {
    	messageProcessor.processError("IMSIPLayer.processTimeout(..) Previous message not sent: " + "timeout");
    }

    /** 
     * This method is called by the SIP stack when there's an asynchronous
     * message transmission error.
     * 
     * @param evt IOExceptionEvent = IOException
     */
    public void processIOException(IOExceptionEvent evt) {
    	messageProcessor.processError("IMSIPLayer.processIOException(..) Previous message not sent: " + "I/O Exception");
    }

    /** 
     * This method is called by the SIP stack when a dialog (session) ends. 
     * 
     * @param evt DialogTerminatedEvent = Dialog Terminated Event
     */
    public void processDialogTerminated(DialogTerminatedEvent evt) {
    }

    /** 
     * This method is called by the SIP stack when a transaction ends.
     * 
     * @param evt TransactionTerminated = transaction terminated
     */
    public void processTransactionTerminated(TransactionTerminatedEvent evt) {
    }

    /**
     * Get my IP address
     * 
     * @return String = IP address
     */
    public String getHost() {
		String host = sipProvider.getListeningPoint("udp").getIPAddress();
		//String host = sipStack.getIPAddress();
		return host;
    }

    /**
     * Get the port this is listening on
     * 
     * @return int = port
     */
    public int getPort() {
		int port = sipProvider.getListeningPoint("udp").getPort();
		return port;
    }

    /**
     * Get my username
     * 
     * @return String = username
     */
    public String getUsername() {
    	return username;
    }

    /**
     * Set my username
     * @param newUsername String = username
     */
    public void setUsername(String newUsername) {
    	username = newUsername;
    }

    /**
     * Returns our message processor
     * 
     * @return SIPMessageProcessor = message processor
     */
    public SIPMessageProcessor getMessageProcessor() {
    	return messageProcessor;
    }

    /**
     * Sets our message processor
     * 
     * @param newMessageProcessor = message processor
     */
    public void setMessageProcessor(SIPMessageProcessor newMessageProcessor) {
    	messageProcessor = newMessageProcessor;
    }

}
