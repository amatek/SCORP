This file describes what should be added to DTNSim.java and SimClock.java so SCORP can work.

It also describes the SlotTimeCheck.java which provides the idea of time slots required for calculations performed by TECI.

NOTE: this implementation considered the Bubble Rap implemented by PJ Dillon which were adapted to run SCORP. Thus, you will find some files in this bundle which you may already have. I recommend you back up similar files and then follow the intructions below if you already have the Bubble Rap on your simulator.
 
In the Sample file you can find an example for running SCORP. It simulates 150 nodes with ShortestPathMapBasedMovement and load comes from an external file. These files should be placed together where one.sh is being called.

NOTE: Regarding the average delivery probability, you must consider the ratio between the number of delivered mgs and the number of messages expected to be delivered as to be able to compare the result of SCORP's performance with other proposals. In this sample file, two messages are created and for each message, there 100 nodes interested. So, you have the expected number of deliveries = 200.

Implemented by Waldir Moreira (waldir.junior@ulusofona.pt).

Changes

i) core.DTNSim

public static void main(String[] args) {
	// TECI **********************************
		ArrayList<Long> arl= new ArrayList<Long>();
		/** 24 slots of 1 hour **/
		arl.add((long)3600);  // 1
		arl.add((long)7200);  // 2
		arl.add((long)10800); // 3
		arl.add((long)14400); // 4
		arl.add((long)18000); // 5
		arl.add((long)21600); // 6
		arl.add((long)25200); // 7
		arl.add((long)28800); // 8
		arl.add((long)32400); // 9
		arl.add((long)36000); // 10
		arl.add((long)39600); // 11
		arl.add((long)43200); // 12
		arl.add((long)46800); // 13
		arl.add((long)50400); // 14
		arl.add((long)54000); // 15
		arl.add((long)57600); // 16
		arl.add((long)61200); // 17
		arl.add((long)64800); // 18
		arl.add((long)68400); // 19
		arl.add((long)72000); // 20
		arl.add((long)75600); // 21
		arl.add((long)79200); // 22
		arl.add((long)82800); // 23
		arl.add((long)86400); // 24
		SlotTimeCheck g = new SlotTimeCheck(arl);
	// TECI **********************************

================================

ii) core.SimClock

public void setTime(double time) {
	clockTime = time;
	SlotTimeCheck.update(time); // TECI: Everytime the simulation time changes the update-method of SlotTimeCheck is called to check if a end of a Slot is already reached.   
}

public static void reset() {
	clockTime = 0;
	SlotTimeCheck.currentday=1; // Restart day count in case of running multiple runs 
}

================================

iii) Add new class SlotTimeCheck, SimScenario, DTNHost, and Message to core (you may want to back up SimScenario, DTNHost and Message)

SlotTimeCheck is the class to manage the SlotSystem. 
Depending on if a slot changed (end of a slot) it initiates metric calculations on all hosts in the simulation.

================================

iv) Add MessageCreateEvent and StandardEventsReader to input (you may want to back up MessageCreateEvent and StandardEventsReader)

================================

v) Add DecisionEngineRouter, RoutingDecisionEngine and MessageRouter to routing (you may want to back up MessageRouter)

================================

vi) Add Scorp, Duration to routing.community (you may need to create this new package)

================================

vii) Add InterestReport to report

