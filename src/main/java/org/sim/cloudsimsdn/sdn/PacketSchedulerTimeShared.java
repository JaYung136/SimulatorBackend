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
		// 距离上一次传输经过的时间
		double timeSpent = currentTime - this.previousTime;

		if(timeSpent <= 0 || this.getInTransmissionNum() == 0) {
			return 0;    // Nothing changed
		}

		// 本次传输的数据量
		double processedThisRound =  (timeSpent * channel.getAllocatedBandwidth());

		// 缓存队列中第一个包，添加已经被传输的长度
		Transmission transmission = inTransmission.get(0);
		transmission.addCompletedLength(processedThisRound);

		// 如果传输完成，将数据包放入completed队列等待之后处理（比如发起接收事件）
		if (transmission.isCompleted()){
			this.completed.add(transmission);
			this.inTransmission.remove(transmission);
		}

		if(processedThisRound == 0){//可以优化，不加判断
			previousTime = currentTime;
		} else {
			previousTime = currentTime;
		}

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
		// step3:以FIFO策略发送数据包
		Transmission transmission = this.inTransmission.get(0);
		// estimated_finish_time = packetSize / bandwidth of channel
		double eft = estimateFinishTime(transmission);
		if (eft<delay)
			delay = eft;

		if(delay == Double.POSITIVE_INFINITY) {
			return delay;
		}

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
