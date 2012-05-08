package edu.mit.csail.whanausip.sip;

import java.rmi.RemoteException;
import java.util.TimerTask;
import java.util.logging.Logger;

import edu.mit.csail.whanausip.commontools.*;
import edu.mit.csail.whanausip.dht.*;

/**
 * Task to run setup periodically
 * This timer runs every SETUP_PERIOD/SETUP_DIVIDER ms.
 * dividerCount keeps track 
 * 
 * @author ryscheng
 * @date 2010/07/29
 */
public class WhanauSetupTimerTask<T> extends TimerTask {

	private long 					dividerCount;	//Used to check which tick
	private WhanauVirtualNode<T> 	whanauNode;		//Reference to node
	private Logger 					log;			//Logger
	
	/**
	 * Creates a new timer task
	 * 
	 * @param log	Logger				= log
	 * @param node	WhanauVirtualNode<T>= node
	 */
	public WhanauSetupTimerTask(Logger log, WhanauVirtualNode<T> node) {
		this.dividerCount = 0;
		this.log = log;
		this.whanauNode = node;
	}
	
	/**
	 * Runs every SETUP_PERIOD/SETUP_DIVIDER ms
	 * Every time SETUP_PERIOD is reached, it will reset the stage to 0
	 * Wait SETUP_DIVIDER ms and then run setup
	 */
	public void run() {
		long originalDividerCount = this.dividerCount;
		this.dividerCount = (this.dividerCount + 1) % SIPCommConstants.SETUP_TIMER_DIVIDER;
		
		
		//Right before setup, reset the stage so that nodes will wait for others
		if (originalDividerCount == 0) {
			this.log.info("Reset setup stage");
			this.whanauNode.getState().setSetupStage(0);
		//Run Setup Part 1
		} else if (originalDividerCount == 1) {
			try {
				this.whanauNode.getControlRef().runSetupPart1Thread(WhanauDHTConstants.W, WhanauDHTConstants.RD, WhanauDHTConstants.RF, WhanauDHTConstants.RS);
			} catch (RemoteException ex) {
				this.log.warning(ex.getMessage());
			}
		//Run Setup Part 2
		} else if (originalDividerCount == 2) {
			try {
				this.whanauNode.getControlRef().runSetupPart2Thread(WhanauDHTConstants.W, WhanauDHTConstants.RD, WhanauDHTConstants.RF, WhanauDHTConstants.RS);
			} catch (RemoteException ex) {
				this.log.warning(ex.getMessage());
			}
		}  
	}

}
