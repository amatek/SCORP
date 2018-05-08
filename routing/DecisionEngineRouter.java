/**
 * DecisionEngineRouter.java was adapted to cope with SCORP.
 * 
 * Copyright 2010 by University of Pittsburgh
 * Copyright 2013 SITI, Universidade Lusófona
 * 
 * SCORP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SCORP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SCORP.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Waldir Moreira (waldir.junior@ulusofona.pt) 
 */

package routing;

import java.util.*;

import core.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SlotTimeCheck;
import core.Tuple;

/**
 * This class overrides ActiveRouter in order to inject calls to a 
 * DecisionEngine object where needed add extract as much code from the update()
 * method as possible. 
 * 
 * <strong>Forwarding Logic:</strong> 
 * 
 * A DecisionEngineRouter maintains a List of Tuple<Message, Connection> in 
 * support of a call to ActiveRouter.tryMessagesForConnected() in 
 * DecisionEngineRouter.update(). Since update() is called so frequently, we'd 
 * like as little computation done in it as possible; hence the List that gets
 * updated when events happen. Four events cause the List to be updated: a new 
 * message from this host, a new received message, a connection goes up, or a 
 * connection goes down. On a new message (either from this host or received 
 * from a peer), the collection of open connections is examined to see if the
 * message should be forwarded along them. If so, a new Tuple is added to the
 * List. When a connection goes up, the collection of messages is examined to 
 * determine to determine if any should be sent to this new peer, adding a Tuple
 * to the list if so. When a connection goes down, any Tuple in the list
 * associated with that connection is removed from the List.
 * 
 * <strong>Decision Engines</strong>
 * 
 * Most (if not all) routing decision making is provided by a 
 * RoutingDecisionEngine object. The DecisionEngine Interface defines methods 
 * that enact computation and return decisions as follows:
 * 
 * <ul>
 *   <li>In createNewMessage(), a call to RoutingDecisionEngine.newMessage() is 
 * 	 made. A return value of true indicates that the message should be added to
 * 	 the message store for routing. A false value indicates the message should
 *   be discarded.
 *   </li>
 *   <li>changedConnection() indicates either a connection went up or down. The
 *   appropriate connectionUp() or connectionDown() method is called on the
 *   RoutingDecisionEngine object. Also, on connection up events, this first
 *   peer to call changedConnection() will also call
 *   RoutingDecisionEngine.doExchangeForNewConnection() so that the two 
 *   decision engine objects can simultaneously exchange information and update 
 *   their routing tables (without fear of this method being called a second
 *   time).
 *   </li>
 *   <li>Starting a Message transfer, a protocol first asks the neighboring peer
 *   if it's okay to send the Message. If the peer indicates that the Message is
 *   OLD or DELIVERED, call to RoutingDecisionEngine.shouldDeleteOldMessage() is
 *   made to determine if the Message should be removed from the message store.
 *   <em>Note: if tombstones are enabled or deleteDelivered is disabled, the 
 *   Message will be deleted and no call to this method will be made.</em>
 *   </li>
 *   <li>When a message is received (in messageTransferred), a call to 
 *   RoutingDecisionEngine.isFinalDest() to determine if the receiving (this) 
 *   host is an intended recipient of the Message. Next, a call to 
 *   RoutingDecisionEngine.shouldSaveReceivedMessage() is made to determine if
 *   the new message should be stored and attempts to forward it on should be
 *   made. If so, the set of Connections is examined for transfer opportunities
 *   as described above.
 *   </li>
 *   <li> When a message is sent (in transferDone()), a call to 
 *   RoutingDecisionEngine.shouldDeleteSentMessage() is made to ask if the 
 *   departed Message now residing on a peer should be removed from the message
 *   store.
 *   </li>
 * </ul>
 * 
 * <strong>Tombstones</strong>
 * 
 * The ONE has the the deleteDelivered option that lets a host delete a message
 * if it comes in contact with the message's destination. More aggressive 
 * approach lets a host remember that a given message was already delivered by
 * storing the message ID in a list of delivered messages (which is called the
 * tombstone list here). Whenever any node tries to send a message to a host 
 * that has a tombstone for the message, the sending node receives the 
 * tombstone.
 * 
 * @author PJ Dillon, University of Pittsburgh
 */
public class DecisionEngineRouter extends ActiveRouter
{
	/** SCORP 
	 * deltaT and startconnectiontime changed from private Map<DTNHost,Double> 
	 * as they now store a list on interests 
	 *
	 * Map to save the starting times a connection of the several nodes ---- 
	 * "-1" if there is no connection at the moment */
	private Map<DTNHost, Map<String,Double>> timeEncounterWithInterests;  
	
	private Map<String,Double> timeWithInterests;    
	
	/** Map to save the connectiontimes of the nodes for the current slot */
	private Map<String,Double> connectedTimeToInterests;    
	
	/** every slot of averageDuration corresponds to a specific slot of a day 
	 * containing the average duration for all the nodes that did set up a connection with THIS node */
	private ArrayList<Map<String,Double>> averageConnectedTimeToInterests;   

	
//	private Map<String, Double> deltaTforImportance;
//	private Map<DTNHost,Double> importancemap; //Stores the importance of encountered nodes
//	private double importance;
//	private FileWriter results;
//	public static FileWriter results2;
	
	/** SCORP */
	public static Map<String, Double> weightToInterests;
	public static Map<DTNHost, Map<String, Double>> weightToInterestsCopy= new HashMap<DTNHost, Map<String, Double>>();
	
	public static Map<DTNHost, Double> importCopy= new HashMap<DTNHost, Double>();
	public static DecisionEngineRouter host;

	int predCount=0;
    /////////////ADDED
	
	public static final String PUBSUB_NS = "DecisionEngineRouter";
	public static final String ENGINE_SETTING = "decisionEngine";
	public static final String TOMBSTONE_SETTING = "tombstones";
	public static final String CONNECTION_STATE_SETTING = "";
	
	protected boolean tombstoning;
	protected RoutingDecisionEngine decider;
	protected List<Tuple<Message, Connection>> outgoingMessages;
	
	protected Set<String> tombstones;
	
	/** how often TTL check (discarding old messages) is performed */
	 public static int TTL_CHECK_INTERVAL = 60;
	 /** sim time when the last TTL check was done */
	 private double lastTtlCheck;
	
	/** 
	 * Used to save state machine when new connections are made. See comment in
	 * changedConnection() 
	 */
	protected Map<Connection, Integer> conStates;
	
	
	
	public DecisionEngineRouter(Settings s)
	{
		super(s);
		
		Settings routeSettings = new Settings(PUBSUB_NS);
		
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		
		decider = (RoutingDecisionEngine)routeSettings.createIntializedObject(
				"routing." + routeSettings.getSetting(ENGINE_SETTING));
		
		if(routeSettings.contains(TOMBSTONE_SETTING))
			tombstoning = routeSettings.getBoolean(TOMBSTONE_SETTING);
		else
			tombstoning = false;
		
		if(tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);
		
		/** CHANGED FROM initPreds() */
		weightToInterests = new HashMap<String, Double>();       /** SCORP */
		//new:
		averageConnectedTimeToInterests=new ArrayList<Map<String,Double>>(SlotTimeCheck.getnumberofslots());
		for(int i=0;i<SlotTimeCheck.getnumberofslots();i++){
			Map<String,Double> map=new HashMap<String,Double>();
			averageConnectedTimeToInterests.add(map);
		}
	//	//System.out.println(""+averageDurations.size());
		timeEncounterWithInterests=new HashMap<DTNHost, Map<String,Double>>(); 		/** SCORP */
		timeWithInterests=new HashMap<String,Double>();
		connectedTimeToInterests=new HashMap<String,Double>();                    /** SCORP */
//		importancemap=new HashMap<DTNHost,Double>();
//		importance=0;
//		try{
//			results=new FileWriter("resultsp17.txt",true);
//		}catch(IOException e){}
//		try{
//			results2=new FileWriter("resultsimportance.txt",true);
//		}catch(IOException e){}
	}

	public DecisionEngineRouter(DecisionEngineRouter r)
	{
		super(r);
		outgoingMessages = new LinkedList<Tuple<Message, Connection>>();
		decider = r.decider.replicate();
		tombstoning = r.tombstoning;
		
		if(this.tombstoning)
			tombstones = new HashSet<String>(10);
		conStates = new HashMap<Connection, Integer>(4);
		
		/** CHANGED FROM initPreds() */
		weightToInterests = new HashMap<String, Double>();      /** SCORP */
		//new:
		averageConnectedTimeToInterests = new ArrayList<Map<String,Double>>(SlotTimeCheck.getnumberofslots());
		for(int i=0;i<SlotTimeCheck.getnumberofslots();i++){
			Map<String,Double> map = new HashMap<String,Double>();
			averageConnectedTimeToInterests.add(map);
		}
		timeEncounterWithInterests=new HashMap<DTNHost, Map<String,Double>>();  		/** SCORP */
		timeWithInterests=new HashMap<String,Double>();
		connectedTimeToInterests=new HashMap<String,Double>();                    /** SCORP */
//		importancemap=new HashMap<DTNHost,Double>();
//		importance=0;
//		try{
//			results=new FileWriter("resultsp17.txt",true);
//		}catch(IOException e){}
//		try{
//			results2=new FileWriter("resultsimportance.txt",true);
//		}catch(IOException e){}
	}

	@Override
	public MessageRouter replicate()
	{
		return new DecisionEngineRouter(this);
	}

	@Override
	 public boolean createNewMessage(Message m){
		if(decider.newMessage(m)){
			//if(m.getId().equals("M7"))
			////System.out.println("Host: " + getHost() + " Creating M7");
			makeRoomForNewMessage(m.getSize());
			m.setTtl(this.msgTtl);
			addToMessages(m, true); 
			findConnectionsForNewMessage(m, getHost());
			return true;
		}
		return false;
	 }
//	public boolean createNewMessage(Message m)
//	{
//		if(decider.newMessage(m))
//		{
//			//if(m.getId().equals("M7"))
//				////System.out.println("Host: " + getHost() + " Creating M7");
//			makeRoomForNewMessage(m.getSize());
//			addToMessages(m, true);
//			
//			findConnectionsForNewMessage(m, getHost());
//			return true;
//		}
//		return false;
//	}
	
	//////////
	/**
	 * public void initPreds() {
		this.weightToInterests = new HashMap<DTNHost, Double>();
	}*/
	///////////
	
	@Override
	public void changedConnection(Connection con)
	{
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		DecisionEngineRouter otherRouter = (DecisionEngineRouter)otherNode.getRouter();
		
		if(con.isUp())
		{
			////System.out.println("\nConn Up: " + myHost + " -> " + otherNode);
			///////////
			keepTrackConnectionStartTime(otherNode);    //new function
		    /** SEE IF APPLICABLE TO SCORP 
		     * updateImportancemap(otherNode);
		     */
			///////////
			//System.out.println("vamos entrar em decider.connectionUp(myHost, otherNode)");
			decider.connectionUp(myHost, otherNode);
			
			/*
			 * This part is a little confusing because there's a problem we have to
			 * avoid. When a connection comes up, we're assuming here that the two 
			 * hosts who are now connected will exchange some routing information and
			 * update their own based on what the get from the peer. So host A updates
			 * its routing table with info from host B, and vice versa. In the real
			 * world, A would send its *old* routing information to B and compute new
			 * routing information later after receiving B's *old* routing information.
			 * In ONE, changedConnection() is called twice, once for each host A and
			 * B, in a serial fashion. If it's called for A first, A uses B's old info
			 * to compute its new info, but B later uses A's *new* info to compute its
			 * new info.... and this can lead to some nasty problems. 
			 * 
			 * To combat this, whichever host calls changedConnection() first calls
			 * doExchange() once. doExchange() interacts with the DecisionEngine to
			 * initiate the exchange of information, and it's assumed that this code
			 * will update the information on both peers simultaneously using the old
			 * information from both peers.
			 */
			if(shouldNotifyPeer(con))
			{
				this.doExchange(con, otherNode);
				otherRouter.didExchange(con);
			}
			
			/*
			 * Once we have new information computed for the peer, we figure out if
			 * there are any messages that should get sent to this peer.
			 */
			Collection<Message> msgs = getMessageCollection();
			for(Message m : msgs)
			{
				//System.out.println("TESTE message: "+m);
				if(decider.shouldSendMessageToHost(m, myHost, otherNode))//ADDED, myHost ALSO CHANGED RoutingDecisionEngine
					outgoingMessages.add(new Tuple<Message,Connection>(m, con));
			}
		}
		else /////////// RETIRAR if(con.isUp())
		{
			//System.out.println("\nConn Down: " + myHost + " -> " + otherNode);
			/////////////
			computeConnectedTime(otherNode);
			/** SEE LATER
			 *  setstarttimeoff(otherNode);
			 */
			/////////////
			decider.connectionDown(myHost, otherNode);
			
			conStates.remove(con);
			
			/*
			 * If we  were trying to send message to this peer, we need to remove them
			 * from the outgoing List.
			 */
			for(Iterator<Tuple<Message,Connection>> i = outgoingMessages.iterator(); 
					i.hasNext();)
			{
				Tuple<Message, Connection> t = i.next();
				if(t.getValue() == con)
					i.remove();
			}
		}
	}
	

	/** Keeps track of the time of encounter with the 
	 *  different interests of users **/
	private void keepTrackConnectionStartTime(DTNHost encounteredHost) {
		List<String> interestEncHost = new ArrayList<String>();
		interestEncHost=encounteredHost.getInterests();
		Map<String,Double> interestSet = new HashMap<String,Double>();
		//System.out.println("this host: " + this.getHost());
		//System.out.println("encounteredHost: "+encounteredHost);
		//System.out.println("time of encounter: " + SimClock.getTime());
		//System.out.println("Interests of encounteredHost: "+interestEncHost);
		
		timeWithInterests.clear();
//		timeEncounterWithInterests.clear();
		
		for(String aIntEncHost : interestEncHost){
			if(!interestSet.containsKey(aIntEncHost)){
				interestSet.put(aIntEncHost, SimClock.getTime());
				timeWithInterests.put(aIntEncHost, SimClock.getTime());
			}
//							
//			else
//				timeEncounterWithInterests.put(aIntEncHost, SimClock.getTime());	
		}
		//System.out.println("timeWithInterests: "+timeWithInterests);
		//System.out.println("startconnectiontime of this host: "+ interestSet);
		timeEncounterWithInterests.put(encounteredHost, interestSet);
		//System.out.println("timeEncounterWithInterests: " + timeEncounterWithInterests);
	}
	
	/** SEE IF APPLICABLE TO SCORP 
	private void updateImportancemap(DTNHost encounteredHost){
		double importa=((DecisionEngineRouter)encounteredHost.getRouter()).getImportance();
	    importancemap.put(encounteredHost, importa);
	}
	*/
	
	/** Compute the time this host has spent with 
	 *  the different interests of users **/
	private void computeConnectedTime(DTNHost encounteredHost){
		Map<String, Double> encHostInterests = new HashMap<String,Double>();
		encHostInterests = timeEncounterWithInterests.get(encounteredHost);
		Set<String> deltaHostToUp= encHostInterests.keySet();
		Iterator<String> deltaIterator=deltaHostToUp.iterator();		
		
		//System.out.println("this host: " + this.getHost());
		//System.out.println("this Host connections: "+ this.getConnections());
		//System.out.println("encounteredHost: "+ encounteredHost);
		//System.out.println("interests of enc host: " + encHostInterests);
		//System.out.println("time of disconnection: " + SimClock.getTime());
		//System.out.println("deltaHostToUp: " + deltaHostToUp);
		//System.out.println("deltaT: "+connectedTimeToInterests);
		
		while(deltaIterator.hasNext()){            
		String currentInterest = deltaIterator.next();
		if(!connectedTimeToInterests.containsKey(currentInterest))
		{
			//System.out.println("currentInterest: "+ currentInterest +" not in delta.");
			connectedTimeToInterests.put(currentInterest, SimClock.getTime()-encHostInterests.get(currentInterest));
		}
		else
		{
			//System.out.println("currentInterest: "+ currentInterest +" already in delta.");
			connectedTimeToInterests.put(currentInterest, connectedTimeToInterests.get(currentInterest)+(SimClock.getTime()-encHostInterests.get(currentInterest)));
		}
	}
	
	//System.out.println("Updated deltaT: "+connectedTimeToInterests);
		
//		/** SCORP */
//		Set<String> deltaHostToUp= timeEncounterWithInterests.keySet();
//		Iterator<String> deltaIterator=deltaHostToUp.iterator();
//		
//		//System.out.println("this host: " + this.getHost());
//		//System.out.println("encounteredHost: "+ this.getConnections());
//		//System.out.println("time of disconnection: " + SimClock.getTime());
//		//System.out.println("deltaHostToUp: " + deltaHostToUp);
//		//System.out.println("deltaT: "+connectedTimeToInterests);
//		
//		while(deltaIterator.hasNext()){            
//			String currentInterest = deltaIterator.next();
//			if(!connectedTimeToInterests.containsKey(currentInterest))
//			{
//				//System.out.println("currentInterest: "+ currentInterest +" not in delta.");
//				connectedTimeToInterests.put(currentInterest, SimClock.getTime()-timeEncounterWithInterests.get(currentInterest));
//			}
//			else
//			{
//				//System.out.println("currentInterest: "+ currentInterest +" already in delta.");
//				connectedTimeToInterests.put(currentInterest, connectedTimeToInterests.get(currentInterest)+(SimClock.getTime()-timeEncounterWithInterests.get(currentInterest)));
//			}
//		}
//		
//		//System.out.println("Updated deltaT: "+connectedTimeToInterests);
		
		/** SCORP */
		
	}
	
	/** SEE LATER
	private void setstarttimeoff(DTNHost host){
		startconnectiontime.put(host,-1.0);
	} 
	*/
	
	/** Called after each daily sample **/
	public void dailySampleDone(){
		/** SCORP */
		//System.out.println("timeEncounterWithInterests: "+timeEncounterWithInterests);
		//System.out.println("deltaT in calcdeltaTandAD: " + connectedTimeToInterests);
		//System.out.println("this.getHost() in calcdeltaTandAD: "+this.getHost());
				
		Iterator<Connection> connections = this.getHost().getConnections().iterator();
		ArrayList<DTNHost> thisHostConnectedToNodes = new ArrayList<DTNHost>(this.getHost().getConnections().size());
	
		if(!this.getHost().getConnections().isEmpty()){
			
			while(connections.hasNext()){
				DTNHost otherHost = connections.next().getOtherNode(this.getHost());
				thisHostConnectedToNodes.add(otherHost);
			}
			//System.out.println("connected to: "+thisHostConnectedToNodes);			
//		}		
//		if(!this.getHost().getConnections().isEmpty()){
			Iterator<DTNHost> connToNodes = thisHostConnectedToNodes.iterator();
			
			while(connToNodes.hasNext()){
				
				DTNHost connTo = connToNodes.next();
				
				//System.out.println("calculando delta para no: " + connTo);
				
				Map<String, Double> intConnTo = timeEncounterWithInterests.get(connTo);				
				Set<String> interestToUp = intConnTo.keySet();
				Iterator<String> intToUpIterator = interestToUp.iterator();
				
			while(intToUpIterator.hasNext()){            
				String currentInterest = intToUpIterator.next();
				if(!connectedTimeToInterests.containsKey(currentInterest))
				{
					//System.out.println("currentInterest: "+ currentInterest +" not in delta.");
					connectedTimeToInterests.put(currentInterest, SimClock.getTime()-intConnTo.get(currentInterest));
				}
				else
				{
					//System.out.println("currentInterest: "+ currentInterest +" already in delta.");
					connectedTimeToInterests.put(currentInterest, connectedTimeToInterests.get(currentInterest)+(SimClock.getTime()-intConnTo.get(currentInterest)));
				}
			}
			//System.out.println("Updated deltaT by calcdeltaTandAD(): "+connectedTimeToInterests);
			
			Iterator<String> ToUpIterator = connTo.getInterests().iterator();
			
			while(ToUpIterator.hasNext()){            
				String currentInterest = ToUpIterator.next();
				//System.out.println("currentInterest: "+currentInterest);
				intConnTo.put(currentInterest,SimClock.getTime());
				//System.out.println("intConnTo: "+intConnTo);
				timeEncounterWithInterests.put(connTo, intConnTo);
				
//				if(connectedTimeToInterests.get(currentInterest)>=0){
//					tempInterestList.put(currentInterest,SimClock.getTime());
//					}
			}
			//System.out.println("timeEncounterWithInterests: "+timeEncounterWithInterests);
			}
		} else {
			Set<String> encounterTimeToUp = timeWithInterests.keySet();
			Iterator<String> encounterTimeIterator = encounterTimeToUp.iterator();
			while(encounterTimeIterator.hasNext()){            
				String currentInterest = encounterTimeIterator.next();
				if(!connectedTimeToInterests.containsKey(currentInterest))
				{
					//System.out.println("currentInterest: "+ currentInterest +" already in delta.");
					connectedTimeToInterests.put(currentInterest, connectedTimeToInterests.get(currentInterest));
				}
			}
			//System.out.println("Updated deltaT by calcdeltaTandAD(): "+ connectedTimeToInterests);
		}
		
		updateAverageConnectedTimeToInterests();
		updateSocialWeightToInterests();
		
		/**Clear for next daily sample**/
		connectedTimeToInterests.clear();
		
		/** SEE LATER 
		 * updateImportance();
		 * */
		
		weightToInterestsCopy.put(this.getHost(),weightToInterests);
		/** SCORP */
	}
	
	/** Called to update the average connected time this host had 
	 *  with the different interests of encountered **/
	private void updateAverageConnectedTimeToInterests(){
		long currentday=SlotTimeCheck.getDay();
		int currentslot = SlotTimeCheck.getcurrentslot();
		Map<String,Double> currentAverageConnectedTimeToInterests = averageConnectedTimeToInterests.get(currentslot);
		Set<String> hostInterests = connectedTimeToInterests.keySet();
		Iterator<String> hostInterestIterator = hostInterests.iterator();
		
		//System.out.println("\nthis host: " + this.getHost());
		//System.out.println("Its connectedTimeToInterests: " + connectedTimeToInterests);
		//System.out.println("Its averageConnectedTimeToInterests: " + averageConnectedTimeToInterests);
			
		while(hostInterestIterator.hasNext()){
			String currentHostInterest = hostInterestIterator.next();
			double oldAD=0;
			if(currentAverageConnectedTimeToInterests.get(currentHostInterest) == null){
				oldAD=0;
			}
			else{
				oldAD=currentAverageConnectedTimeToInterests.get(currentHostInterest);
			}
			//System.out.println("old AD: " + oldAD);
			
			double newAD;
			
			if(connectedTimeToInterests.get(currentHostInterest)!=null){
				newAD = (connectedTimeToInterests.get(currentHostInterest)+(currentday-1)*oldAD);
				currentAverageConnectedTimeToInterests.put(currentHostInterest,newAD);	
			}
			else{
				newAD = (currentday-1)*oldAD;
				currentAverageConnectedTimeToInterests.put(currentHostInterest,newAD);	
			}
			//System.out.println("new AD: " + newAD);
		}
		Set<String> interestsToUp = currentAverageConnectedTimeToInterests.keySet();
		Iterator<String> interestsToUpIterator = interestsToUp.iterator();
		double newvalue = 0.0;
		while(interestsToUpIterator.hasNext()){
			String toUpdate = interestsToUpIterator.next();
			if(!hostInterests.contains(toUpdate)){
				//System.out.println("if do AD ");
				newvalue= (currentday-1)*currentAverageConnectedTimeToInterests.get(toUpdate)/currentday;
			}
			else{
				//System.out.println("else do AD");
				newvalue= currentAverageConnectedTimeToInterests.get(toUpdate)/currentday;
			}
			currentAverageConnectedTimeToInterests.put(toUpdate,newvalue);
		}
//		deltaTforImportance = connectedTimeToInterests;
		connectedTimeToInterests = new HashMap<String,Double>();
		//System.out.println("Its averageConnectedTimeToInterests after updateAverageDuration(): " + averageConnectedTimeToInterests);
		//System.out.println("Its currentAverageDurationSlot: " + currentAverageConnectedTimeToInterests);
	}
	
	/** Called to update the social weight of this node 
	 *  towards the different interests of encountered users **/
	private void updateSocialWeightToInterests(){
		Map<String,Double> socialWeightToInterests = new HashMap<String,Double>();
		int numberofslots = SlotTimeCheck.getnumberofslots();
		double denominator = SlotTimeCheck.getnumberofslots();
		int slotindex= SlotTimeCheck.getcurrentslot();
		for(int i=numberofslots;i>0;i--){
			if(averageConnectedTimeToInterests.get(slotindex) != null){
				Map<String, Double> currentACTTI = averageConnectedTimeToInterests.get(slotindex);
				Set<String> averageTimeToInterests = currentACTTI.keySet();
				Iterator<String> averageTimeToInterestsIterator = averageTimeToInterests.iterator();
				while(averageTimeToInterestsIterator.hasNext()){
					String currentAD = averageTimeToInterestsIterator.next();
					double currAverageduration = currentACTTI.get(currentAD);
					if(socialWeightToInterests.get(currentAD)==null){
						socialWeightToInterests.put(currentAD, 0.0);
					}
//			//		//System.out.println(""+(nextweight.get(currentHost)+(((double)i)/(SlotTimeCheck.getnormalisation()))*currAverageduration));
					// nextweight.put(currentHost, nextweight.get(currentHost)+(((double)i)/(SlotTimeCheck.getnormalisation()))*currAverageduration);
					socialWeightToInterests.put(currentAD, socialWeightToInterests.get(currentAD)+((SlotTimeCheck.getnumberofslots())/(denominator))*currAverageduration);
				}
				denominator++;
				slotindex=(slotindex+1);
				if(slotindex==SlotTimeCheck.getnumberofslots()){
					slotindex=0;
				}
			}
		}
		this.weightToInterests=socialWeightToInterests;
		//System.out.println("weightToInterests: " + socialWeightToInterests +"\n");
	}
	
	/**
	private void updateImportance(){
		int nrofneighbours=deltaTforImportance.size();
		double sumofweights=0;
		Set<DTNHost>set =deltaTforImportance.keySet();
		Iterator<DTNHost> it= set.iterator();
		while(it.hasNext()){
			DTNHost ho=it.next();
			sumofweights=sumofweights+weightToInterests.get(ho);
			
		}
	//	//System.out.println(""+preds.size());
		Set<DTNHost> deltahosts = deltaTforImportance.keySet();
		Iterator<DTNHost> iter= deltahosts.iterator();
		double newimportance=0;

//		try{
//		results2.write("This host: " + this.getHost()+"\n");
//		results2.write("Its importance before: "+ this.importance+"\n");
//		results2.write("Its neighbor set: " + deltahosts.size() +"\n");
//		}catch(Exception e){}
		
		while(iter.hasNext()){
			DTNHost host=iter.next();
			//			newimportance = newimportance+(preds.get(host)*importancemap.get(host))/(nrofneighbours*sumofweights);
			newimportance = newimportance+(weightToInterests.get(host)*importancemap.get(host))/(nrofneighbours);
//			newimportance = newimportance+importancemap.get(host)/(nrofneighbours);
//			try{
//				results2.write("Other host: " + host + ". Weight to it: " + preds.get(host) + ". Its importance: "+ importancemap.get(host)+"\n");
//				}catch(Exception e){}
		}
		this.importance=0.2+0.8*newimportance;
		importCopy.put(this.getHost(), this.importance);
//		try{
//			results2.write("Its importance now: "+ this.importance+"\n");
//			results2.write("-----------------------\n");
//		}catch(Exception e){}
//		int nrofneighbours=preds.size();
//	//	//System.out.println(""+preds.size());
//		Set<DTNHost> weighthosts = preds.keySet();
//		Iterator<DTNHost> iter= weighthosts.iterator();
//		double newimportance=0;
//		while(iter.hasNext()){
//			DTNHost host=iter.next();
//			newimportance = newimportance+(preds.get(host)*importancemap.get(host))/nrofneighbours;
//		}
//		this.importance=newimportance;
//	//    //System.out.println(""+newimportance);
	}

	public double getImportance(){
		return importance;
	}
	*/
	public Map<String, Double> getweightToInterests() {
		//	ageDeliveryPreds(); // make sure the aging is done
			return this.weightToInterests;
		}
////////////////
	
	protected void doExchange(Connection con, DTNHost otherHost)
	{
		conStates.put(con, 1);
		decider.doExchangeForNewConnection(con, otherHost);
	}
	
	/**
	 * Called by a peer DecisionEngineRouter to indicated that it already 
	 * performed an information exchange for the given connection.
	 * 
	 * @param con Connection on which the exchange was performed
	 */
	protected void didExchange(Connection con)
	{
		conStates.put(con, 1);
	}
	
	@Override
	protected int startTransfer(Message m, Connection con)
	{
		int retVal;
		
		if (!con.isReadyForTransfer()) {
			return TRY_LATER_BUSY;
		}
		
		retVal = con.startTransfer(getHost(), m);
		if (retVal == RCV_OK) { // started transfer
			addToSendingConnections(con);
		}
		else if(tombstoning && retVal == DENIED_DELIVERED)
		{
			this.deleteMessage(m.getId(), false);
			tombstones.add(m.getId());
		}
		else if (deleteDelivered && (retVal == DENIED_OLD || retVal == DENIED_DELIVERED) && 
				decider.shouldDeleteOldMessage(m, con.getOtherNode(getHost()))) {
			/* final recipient has already received the msg -> delete it */
			//if(m.getId().equals("M7"))
				////System.out.println("Host: " + getHost() + " told to delete M7");
			this.deleteMessage(m.getId(), false);
		}
		
		return retVal;
	}

	@Override
	 public int receiveMessage(Message m, DTNHost from){
		int recvCheck = checkReceiving(m); 
		if (recvCheck != RCV_OK) {
			return recvCheck;
		}
		if(isDeliveredMessage(m) || (tombstoning && tombstones.contains(m.getId())))
			return DENIED_DELIVERED; 
		
	 return super.receiveMessage(m, from);
	 }
//	public int receiveMessage(Message m, DTNHost from)
//	{
//		if(isDeliveredMessage(m) || (tombstoning && tombstones.contains(m.getId())))
//			return DENIED_DELIVERED;
//			
//		return super.receiveMessage(m, from);
//	}

	@Override
	public Message messageTransferred(String id, DTNHost from)
	{
		Message incoming = removeFromIncomingBuffer(id, from);
	
		if (incoming == null) {
			throw new SimError("No message with ID " + id + " in the incoming "+
					"buffer of " + getHost());
		}
		
		incoming.setReceiveTime(SimClock.getTime());
		
		Message outgoing = incoming;
		for (Application app : getApplications(incoming.getAppID())) {
			// Note that the order of applications is significant
			// since the next one gets the output of the previous.
			outgoing = app.handle(outgoing, getHost());
			if (outgoing == null) break; // Some app wanted to drop the message
		}
		
		Message aMessage = (outgoing==null)?(incoming):(outgoing);
		/*** ADD getHost().getInterests().contains(aMessage.getContentType())
		 * SCORP ***/
		boolean isFinalRecipient = decider.isFinalDest(aMessage, getHost(), 
				getHost().getInterests().contains(aMessage.getContentType()));
		boolean isFirstDelivery =  isFinalRecipient && 
			!isDeliveredMessage(aMessage);
		
		if (outgoing!=null && decider.shouldSaveReceivedMessage(aMessage, getHost())) 
		{
			// not the final recipient and app doesn't want to drop the message
			// -> put to buffer
			addToMessages(aMessage, false);
			
			// Determine any other connections to which to forward a message
			findConnectionsForNewMessage(aMessage, from);
		}
		
		if (isFirstDelivery)
		{
			this.deliveredMessages.put(id, aMessage);
		}
		
		for (MessageListener ml : this.mListeners) {
			ml.messageTransferred(aMessage, from, getHost(),
					isFirstDelivery);
		}
		
		return aMessage;
	}

	@Override
	protected void transferDone(Connection con)
	{
		Message transferred = this.getMessage(con.getMessage().getId());
		
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(t.getKey().getId().equals(transferred.getId()) && 
					t.getValue().equals(con))
			{
				i.remove();
				break;
			}
		}
		
		if(decider.shouldDeleteSentMessage(transferred, con.getOtherNode(getHost()), getHost()))
		{
			//if(transferred.getId().equals("M7"))
				////System.out.println("Host: " + getHost() + " deleting M7 after transfer");
			this.deleteMessage(transferred.getId(), false);
			
			
		}
	}

	@Override
	public void update(){
		super.update();

		/* time to do a TTL check and drop old messages? Only if not sending */
		if (SimClock.getTime() - lastTtlCheck >= TTL_CHECK_INTERVAL && 
				sendingConnections.size() == 0) {
			dropExpiredMessages();
			lastTtlCheck = SimClock.getTime();
		}

		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		} 

		tryMessagesForConnected(outgoingMessages); 

		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); i.hasNext();){
			Tuple<Message, Connection> t = i.next();
			if(!this.hasMessage(t.getKey().getId())){
				i.remove();
			}
		}
	 }
//	public void update()
//	{
//		super.update();
//		if (!canStartTransfer() || isTransferring()) {
//			return; // nothing to transfer or is currently transferring 
//		}
//		
//		tryMessagesForConnected(outgoingMessages);
//		
//		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
//			i.hasNext();)
//		{
//			Tuple<Message, Connection> t = i.next();
//			if(!this.hasMessage(t.getKey().getId()))
//			{
//				i.remove();
//			}
//		}
//	}
	
	@Override
	public void deleteMessage(String id, boolean drop)
	{
		super.deleteMessage(id, drop);
		
		for(Iterator<Tuple<Message, Connection>> i = outgoingMessages.iterator(); 
		i.hasNext();)
		{
			Tuple<Message, Connection> t = i.next();
			if(t.getKey().getId().equals(id))
			{
				i.remove();
			}
		}
	}

	public RoutingDecisionEngine getDecisionEngine()
	{
		return this.decider;
	}

	protected boolean shouldNotifyPeer(Connection con)
	{
		Integer i = conStates.get(con);
		return i == null || i < 1;
	}
	
	protected void findConnectionsForNewMessage(Message m, DTNHost from)
	{
		//for(Connection c : getHost()) 
		for(Connection c : getConnections())
		{
			DTNHost other = c.getOtherNode(getHost());
			if(other != from && decider.shouldSendMessageToHost(m, getHost(), other))//ADDED, getHost()
			{
				//if(m.getId().equals("M7"))
					////System.out.println("Adding attempt for M7 from: " + getHost() + " to: " + other);
				outgoingMessages.add(new Tuple<Message, Connection>(m, c));
			}
		}
	}
}
