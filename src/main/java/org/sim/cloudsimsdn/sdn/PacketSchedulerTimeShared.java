package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.virtualcomponents.Channel;
import org.sim.cloudsimsdn.sdn.workload.Transmission;
import org.springframework.boot.ExitCodeEvent;

import java.util.List;
/**
 * Network packet scheduler implementing time shared approach.
 * Total physical bandwidth will be allocated to the first transmission in full.
 * Once the 1st transmission is completed, the 2nd transmission will get the full bandwidth, and so on.
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */

public class PacketSchedulerTimeShared extends PacketSchedulerSpaceShared {
	public PacketSchedulerTimeShared(Channel ch) {
		super(ch);
	}

	@Override
	public double updatePacketProcessing() {
		double currentTime = CloudSim.clock();
		double timeSpent = currentTime - this.previousTime;//NetworkOperatingSystem.round(currentTime - this.previousTime);

		if(timeSpent <= 0 || this.getInTransmissionNum() == 0) {
//			System.out.println("Error不应到此处");
			return 0;    // Nothing changed
		}

		//update the amount of transmission
		double processedThisRound =  (timeSpent * channel.getAllocatedBandwidth());

		//update transmission table; remove finished transmission
		Transmission transmission = inTransmission.get(0);
		transmission.addCompletedLength(processedThisRound);

		if (transmission.isCompleted()){
			this.completed.add(transmission);
			this.inTransmission.remove(transmission);
			System.out.println("DEBUG:"+transmission.toString()+"completed");
		}


		if(processedThisRound == 0){
			previousTime = currentTime; //对于disable的wirelesschannel，可以不更进时间
		} else {
			previousTime = currentTime;
		}

		//Log.printLine(CloudSim.clock() + ": Channel.updatePacketProcessing() ("+this.toString()+"):Time spent:"+timeSpent+
		//		", BW/host:"+channel.getAllocatedBandwidth()+", Processed:"+processedThisRound);

		List<Transmission> timeoutTransmission = getTimeoutTransmissions();
		this.timeoutTransmission.addAll(timeoutTransmission);
		this.inTransmission.removeAll(timeoutTransmission);

		return processedThisRound;
	}

	// The earliest finish time among all transmissions in this channel

	/**
	 * 若channel的带宽为0，返回Double.POSITIVE_INFINITY
	 */
	@Override
	public double nextFinishTime() {
		//now, predicts delay to next transmission completion
		double delay = Double.POSITIVE_INFINITY;

		Transmission transmission = this.inTransmission.get(0);
		double eft = estimateFinishTime(transmission);
		if (eft<delay)
			delay = eft;

		if(delay == Double.POSITIVE_INFINITY) {
			return delay;
		}
		//TODO:取消packetscheduler的MinTimeBetweenEvents
//		else if(Math.abs(delay) < CloudSim.getMinTimeBetweenEvents()) {
//			return CloudSim.getMinTimeBetweenEvents();
//		}
		if(delay < 0) {
			throw new RuntimeException("Channel.nextFinishTime: delay: "+delay);
			//System.err.println("Channel.nextFinishTime: delay is minus: "+delay);
		}
		return delay;
	}

	// Estimated finish time of one transmission
	@Override
	public double estimateFinishTime(Transmission t) {
		double bw = channel.getAllocatedBandwidth();

		if(bw == 0) {
			return Double.POSITIVE_INFINITY;
		}

		if(bw < 0 || t.getSize() < 0) {
			throw new RuntimeException("PacketSchedulerTimeShared.estimateFinishTime(): Channel:"+channel+", BW: "+bw + ", Transmission:"+t+", Tr Size:"+t.getSize());
		}

		double eft= (double)t.getSize()/bw;
		return eft;
	}
}
