/**
 * InterestReport.java is part of SCORP.
 * 
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
 * 
 */

package report;

import java.util.*;

import core.*;
/**
 * <p>Reports the number of unique interests and the number of nodes per interest and these nodes.</p>
 * 
 * @author Waldir Moreira, waldir.junior@ulusofona.pt 
 */
public class InterestReport extends Report
{
	public InterestReport()
	{
		init();
	}


	@Override
	public void done()
	{
		List<DTNHost> nodes = SimScenario.getInstance().getHosts();
		Map<String,Integer> interests = new HashMap<String,Integer>();
		Map<String,List<DTNHost>> listOfNodes = new HashMap<String,List<DTNHost>>();
//		System.out.println("hosts: " + nodes);
			
		for(DTNHost h : nodes)
		{
			List<String> temp = new ArrayList<String>();
			temp=h.getInterests();
//			System.out.println("lista interesses: " + temp + " do nó: " + h);
			int increment;
			
			for(String i : temp){
//				System.out.println("interesse i em temp: " +i);
//				System.out.println("interests.containsValue(i): "+interests.containsValue(i));
				if(!interests.containsKey(i)){
					interests.put(i, 1);
//					System.out.println("interests detectados: "+interests);
				}else{
					increment = interests.get(i);
//					System.out.println("increment: "+increment);
					interests.put(i, increment+1);
//					System.out.println("interests incrementados: "+interests);
				}		
			}
		}

		// print the number of interests, number of nodes per interest and the nodes
			Set<String> interest = interests.keySet();
			Iterator<String> iter= interest.iterator();
			List<DTNHost> hosts = new ArrayList<DTNHost>();
			write("number of interests = " + interest.size() + "\n---------");
			write("interest\tnrOfNodes\tnodes with this interest");
			while(iter.hasNext()){
				String next = iter.next();				
				for(DTNHost h : nodes){
					if(h.getInterests().contains(next))
						hosts.add(h);
				}
				write("" + next + "\t\t" + interests.get(next) + "\t\t" + hosts.toString());
				hosts.clear();
			}
		super.done();
	}

	
}
