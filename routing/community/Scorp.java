/**
 * SCORP implementation by Waldir Moreira (waldir.junior@ulusofona.pt).
 * This class, as it is, implements SCORP. 
 * This code was done based on DistributedBubbleRap.java implementation, thus it inherits some functions, methods and classes
 * Despite the fact this class is under the community folder, it does not use any community parameters. 
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
 */

package routing.community;

import java.util.*;
import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SCORP **/
public class Scorp implements RoutingDecisionEngine{
	
	protected Map<DTNHost, Double> startTimestamps;
	protected Map<DTNHost, List<Duration>> connHistory;
	
	private  Map<DTNHost, Map<String, Double>> _weightToInterests;
	private  Map<DTNHost, Double> _importances;

	
	/**
	 * Constructs a SCORP Decision Engine based upon the settings
	 * defined in the Settings object parameter. 
	 * 
	 * @param s Settings to configure the object
	 */
	public Scorp(Settings s){
		this._weightToInterests = new HashMap<DTNHost, Map<String, Double>>();
		this._importances = new HashMap<DTNHost, Double>();
	}
	
	/**
	 * Constructs a SCORP Decision Engine from the argument 
	 * prototype. 
	 * 
	 * @param proto Prototype SCORP upon which to base this object
	 */
	public Scorp(Scorp proto)
	{
		this._weightToInterests = new HashMap<DTNHost, Map<String, Double>>();
		this._importances = new HashMap<DTNHost, Double>();
		startTimestamps = new HashMap<DTNHost, Double>();
		connHistory = new HashMap<DTNHost, List<Duration>>();
		
	}

	public void connectionUp(DTNHost thisHost, DTNHost peer){}

	/**
	 * Starts timing the duration of this new connection and informs the community
	 * detection object that a new connection was formed.
	 * 
	 * @see routing.RoutingDecisionEngine#doExchangeForNewConnection(core.Connection, core.DTNHost)
	 */
	public void doExchangeForNewConnection(Connection con, DTNHost peer)
	{
		DTNHost myHost = con.getOtherNode(peer);
		Scorp de = this.getOtherDecisionEngine(peer);
		
		this.startTimestamps.put(peer, SimClock.getTime());
		de.startTimestamps.put(myHost, SimClock.getTime());
		
//		this.community.newConnection(myHost, peer, de.community);

	}
	
	public void connectionDown(DTNHost thisHost, DTNHost peer)
	{
		double time = startTimestamps.get(peer);
		double etime = SimClock.getTime();
		
		// Find or create the connection history list
		List<Duration> history;
		if(!connHistory.containsKey(peer))
		{
			history = new LinkedList<Duration>();
			connHistory.put(peer, history);
		}
		else
			history = connHistory.get(peer);
		
		// add this connection to the list
		if(etime - time > 0)
			history.add(new Duration(time, etime));
		
		startTimestamps.remove(peer);

	}


	
	public boolean newMessage(Message m){
		return true; // Always keep and attempt to forward a created message
	}

	public boolean isFinalDest(Message m, DTNHost aHost, boolean itIsFinal){
		return aHost.getInterests().contains(m.getContentType());
	}

	public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost){
		Map<String,Double> tempListThis = new HashMap<String,Double>();

		_weightToInterests=DecisionEngineRouter.weightToInterestsCopy;
		double ThisWeightToInterest = 0.0;
		
		if(_weightToInterests.containsKey(thisHost)){
			tempListThis=_weightToInterests.get(thisHost);
			Set<String> interestThis= tempListThis.keySet();
			Iterator<String> hostIterator1=interestThis.iterator();
			if(tempListThis.size()!=0){
				while(hostIterator1.hasNext()){            
					String interest = hostIterator1.next();
					if(interest.equalsIgnoreCase(m.getContentType())){
						ThisWeightToInterest=tempListThis.get(interest);	
					}
				}
			}
		}
		
		if (ThisWeightToInterest>0.0){
			return true;
		}
		return thisHost.getInterests().contains(m.getContentType());
	}

	public boolean shouldSendMessageToHost(Message m, DTNHost thisHost, DTNHost otherHost)
	{
		_weightToInterests=DecisionEngineRouter.weightToInterestsCopy;
		_importances=DecisionEngineRouter.importCopy;
		String messageContent = m.getContentType();
		
		if(checkMessage(m, otherHost)){ 
			return false;
		}
		
		else if(otherHost.getInterests().contains(messageContent)){
			return true; // trivial to deliver to final dest
		}
		
		/*
		 * Here is where we decide when to forward along a message.  
		 */
		
		else if(_weightToInterests.containsKey(thisHost) || _weightToInterests.containsKey(otherHost)){ 
	
			Map<String, Double> tempListThis = new HashMap<String,Double>();
			Map<String,Double> tempListOther = new HashMap<String,Double>();
			
			double ThisWeightToInterest = 0.0;
			double OtherWeightToInterest = 0.0;
			
			tempListThis=_weightToInterests.get(thisHost);
			tempListOther=_weightToInterests.get(otherHost);
			
			Set<String> hostset= tempListThis.keySet();
			Iterator<String> hostIterator=hostset.iterator();
			
			if(tempListThis.size()!=0){
				while(hostIterator.hasNext()){
					String currenthost = hostIterator.next();
					if(currenthost.equalsIgnoreCase(messageContent)){
						ThisWeightToInterest=tempListThis.get(currenthost);	
					}							
				}
			} 
					
			Set<String> hostset1= tempListOther.keySet();
			Iterator<String> hostIterator1=hostset1.iterator();
			
			if(tempListOther.size()!=0){
				while(hostIterator1.hasNext()){            
					String currenthost = hostIterator1.next();
					if(currenthost.equalsIgnoreCase(messageContent)){
						OtherWeightToInterest=tempListOther.get(currenthost);	
					}
				}
			}
							
			if(OtherWeightToInterest>ThisWeightToInterest){
				return true; //other node has better weight
			}

		}
		return false; 
	}
	
	//ADDED Checks whether a nodes has a weight to a specific destination
	public boolean checkWeightToDest(Map<DTNHost,Double> weightList, DTNHost dest){
		if(weightList.get(dest)!=null){
		Set<DTNHost> hostset= weightList.keySet();
		Iterator<DTNHost> hostIterator=hostset.iterator();
			if(weightList.size()!=0){
				while(hostIterator.hasNext()){
					DTNHost currenthost = hostIterator.next();
					if(currenthost==dest)
						return true;
					}
				} 
		}
		return false;	
	}
	
	//ADDED Checks whether a nodes has already a given message
	public boolean checkMessage (Message m, DTNHost otherHost){
		List<Message> teste = new ArrayList<Message>();
		teste.addAll(otherHost.getMessageCollection());				
		Object [] array = teste.toArray();
		String a = m.toString();
		for (int i = 0; i < array.length; i++){
			if(array[i].toString()==a){
				return true;
			}
						
		}
		return false;
	}

	public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost, DTNHost thisHost){
		Map<String,Double> tempListThis = new HashMap<String,Double>();

		_weightToInterests=DecisionEngineRouter.weightToInterestsCopy;
		double ThisWeightToInterest = 0.0;
		
		if(_weightToInterests.containsKey(thisHost)){
			tempListThis=_weightToInterests.get(thisHost);
			Set<String> interestThis= tempListThis.keySet();
			Iterator<String> hostIterator1=interestThis.iterator();
			if(tempListThis.size()!=0){
				while(hostIterator1.hasNext()){            
					String interest = hostIterator1.next();
					if(interest.equalsIgnoreCase(m.getContentType())){
						ThisWeightToInterest=tempListThis.get(interest);	
					}
				}
			}
		}
			
			if(!thisHost.getInterests().contains(m.getContentType()) && ThisWeightToInterest == 0.0){
				return true;
			}
			else if (ThisWeightToInterest>0){
				return false;
			}				
		return false;
	}

	public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld)
	{
		Scorp de = this.getOtherDecisionEngine(hostReportingOld);
		return false; 
	}

	public RoutingDecisionEngine replicate()
	{
		return new Scorp(this);
	}
	
	private Scorp getOtherDecisionEngine(DTNHost h)
	{
		MessageRouter otherRouter = h.getRouter();
		assert otherRouter instanceof DecisionEngineRouter : "This router only works " + 
		" with other routers of same type";
		
		return (Scorp) ((DecisionEngineRouter)otherRouter).getDecisionEngine();
	}

}
