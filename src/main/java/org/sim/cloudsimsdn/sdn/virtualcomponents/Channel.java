/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.virtualcomponents;

import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.Configuration;
import org.sim.cloudsimsdn.sdn.PacketScheduler;
import org.sim.cloudsimsdn.sdn.PacketSchedulerTimeShared;
import org.sim.cloudsimsdn.sdn.monitor.MonitoringValues;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Link;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.GatewaySwitch;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.IntercloudSwitch;
import org.sim.cloudsimsdn.sdn.workload.Transmission;

import java.util.List;

/**
 * This class represents a channel for transmission of data between switches.
 * It controls sharing of available bandwidth. Relation between
 * Transmission and Channel is the same as Cloudlet and CloudletScheduler,
 * but here we consider only the time shared case, representing a shared
 * channel among different simultaneous packet transmissions.
 *
 * This is logical channel. One physical link (class Link) can hold more than one logical channels (class Channel).
 * Channel is directional. It is one way.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Channel {
	public boolean isWireless;
	public int wirelessLevel = 0;
	public List<Node> nodes; //组成本channel的节点
	public List<Node> nodesAll;//从src到dest经过的所有节点。可能涉及跨平台，有好几段channel
	public List<Link> links; //组成本channel的链路
	public List<Link> linksAll;//从src到dest经过的所有链路
	public double allocatedBandwidth; //分配的带宽
	private double previousTime;
	private final int srcId;
	private final int dstId;
	private final int chId;// channelID
	public double BandwidthBackup;// 带宽的备份，用于 enable / disable channel
	public double totalLatency = 0;//不使用

	private SDNVm srcVm;
	public PacketScheduler packetScheduler = new PacketSchedulerTimeShared(this);//channel内部的发包调度器

	public Channel(int chId, int srcId, int dstId, List<Node> nodes, List<Link> links, double bandwidth, SDNVm srcVm, SDNVm dstVm, boolean wireless, int wirelessLevel) {
		//是否经过无线网
		this.isWireless = wireless;
		/**
		 * wirelessLevel=0: 发送方以太网内的一条channel： srcHost -> GatewaySwitch
		 * wirelessLevel=1: 上行无线channel： GatewaySwitch -> InterCloudSwitch(无线网)
		 * wirelessLevel=2: 下行无线channel： InterCloudSwitch(无线网) -> 接收平台的GatewaySwitch
		 * wirelessLevel=3: 接收方以太网内的一条channel： GatewaySwitch -> destHost
		 * 如果不过经无线网，wirelessLevel参数不被使用
		 */
		this.wirelessLevel = wirelessLevel;
		this.chId = chId;
		this.srcId = srcId;
		this.dstId = dstId;
		int gatewayIndex = -1; //GatewaySwitch在nodesAll中的下标
		int interCloudIndex = -1; //InterCloudSwitch在nodesAll中的下标
		if (isWireless) {
			switch (wirelessLevel){
				case 0: // 发送方有线网： srcHost -> GatewaySwitch
					for (int i=0; i<nodes.size(); ++i){
						Node node_i = nodes.get(i);
						if (node_i instanceof GatewaySwitch){// 碰见第一个 GatewaySwitch
							gatewayIndex = i;
							break;
						}
					}
					this.nodes = nodes.subList(0, gatewayIndex+1); //srcHost -> GatewaySwitch
					this.links = links.subList(0, gatewayIndex);
					break;
				case 1: // 上传无线网： GatewaySwitch -> InterCloudSwitch(无线网)
					for (int i=0; i<nodes.size(); ++i){
						Node node_i = nodes.get(i);
						if (node_i instanceof GatewaySwitch){// 碰见第一个 GatewaySwitch
							gatewayIndex = i;
						}
						if (node_i instanceof IntercloudSwitch){// 碰见 IntercloudSwitch
							interCloudIndex = i;
							break;
						}
					}
					this.nodes = nodes.subList(gatewayIndex, interCloudIndex+1);
					this.links = links.subList(gatewayIndex, interCloudIndex);
					break;
				case 2: // 无线网下行： InterCloudSwitch -> GatewaySwitch
					for (int i=0; i<nodes.size(); ++i){
						Node node_i = nodes.get(i);
						if (node_i instanceof IntercloudSwitch){// 碰见 IntercloudSwitch
							interCloudIndex = i;
						}
						if (node_i instanceof GatewaySwitch && interCloudIndex > 0){// 碰见第二个 GatewaySwitch
							gatewayIndex = i;
							break;
						}
					}
					this.nodes = nodes.subList(interCloudIndex, gatewayIndex+1);
					this.links = links.subList(interCloudIndex, gatewayIndex);
					break;
				case 3: // 接收方有线网： GatewaySwitch -> destHost
					for (int i=0; i<nodes.size(); ++i){
						Node node_i = nodes.get(i);
						if (node_i instanceof IntercloudSwitch){// 碰见 IntercloudSwitch
							interCloudIndex = i;
						}
						if (node_i instanceof GatewaySwitch && interCloudIndex > 0){// 碰见第二个 GatewaySwitch
							gatewayIndex = i;
							break;
						}
					}
					this.nodes = nodes.subList(gatewayIndex, nodes.size());
					this.links = links.subList(gatewayIndex, links.size());
					break;
				default:
					this.nodes = nodes;
					this.links = links;
			}
		} else {
			this.nodes = nodes;
			this.links = links;
		}
		//如果isWireless是false，直接跳到这里。nodesAll就是nodes，linksAll就是links
		this.nodesAll = nodes;
		this.linksAll = links;
		this.allocatedBandwidth = this.BandwidthBackup = bandwidth;

		this.srcVm = srcVm;
		packetScheduler.setTimeOut(Configuration.TIME_OUT); // If the packet is not successfull in 3 seconds, it will be discarded.
	}

	public void enableChannel(){
		this.allocatedBandwidth = this.BandwidthBackup;
	}

	public void disableChannel(){
		this.allocatedBandwidth = 0;
	}
//	/* 用于 debug，ignore it */
//	public static void main(String[] args) {
//		List<String> list = new ArrayList<String>(Arrays.asList("大少", "二少", "三少"));
//		List<String> link = list.subList(0,  1);
//
//		System.out.println("\nlalalala\n");
//	}

	public void initialize() {
		// Assign BW to all links
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			Link link = links.get(i);

			link.addChannel(from, this);

			from.updateNetworkUtilization();

			this.totalLatency += link.getLatencyInSeconds();
		}

		nodes.get(nodes.size()-1).updateNetworkUtilization();
	}

	public void terminate() {
		// Remove this channel from all links and nodes
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			Link link = links.get(i);

			link.removeChannel(from, this);

			from.updateNetworkUtilization();
		}
		nodes.get(nodes.size()-1).updateNetworkUtilization();
	}

	private void updateLinks() {
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			Link link = links.get(i);
			link.updateChannel(from, this);
		}
	}

	public void updateRoute(List<Node> nodes, List<Link> links) {
		// Remove this channel from old route
		terminate();

		// Change the nodes and links
		this.nodes = nodes;
		this.links = links;

		// Initialize with the new route
		initialize();
	}

	private double getLowestSharedBandwidth() {
		// Get the lowest bandwidth along links in the channel
		double lowestSharedBw = Double.POSITIVE_INFINITY;

		/* 依次计算每条link可以给本channel的带宽（linkBW/number），取最小值 */
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			//Node to = nodes.get(i+1);
			Link link = links.get(i);

//			link.getSharedBandwidthPerChannel(from) = link.getBw() / link.getChannelCount(from)
			if(lowestSharedBw > link.getSharedBandwidthPerChannel(from))
				lowestSharedBw = link.getSharedBandwidthPerChannel(from);
		}
		return lowestSharedBw;

	}

	public double getAdjustedRequestedBandwidth() {
		double lowest_factor = 1.0;

		// Find the slowest link (lowest bw) among all links where this channel is passing through
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			Link link = links.get(i);

			double factor = link.getDedicatedChannelAdjustFactor(from);
			if(lowest_factor > factor)
				lowest_factor = factor;
		}

		return lowest_factor;

	}

	// Channel adjustment!
	private boolean allocateMoreAvailableBw = false; // if true, we allocate leftover BW to channels (will get more than requested)

	/**
	 * 重新计算channel上的最小带宽
	 */
//	public boolean adjustDedicatedBandwidthAlongLink() {
//		if(chId != -1 && chId != 2999)
//			return false;
////		double factor = this.getAdjustedRequestedBandwidth();
////		double requestedBandwidth = this.getRequestedBandwidth() * factor;
////		if(factor < 1.0) {
////			System.err.println("Channel.adjustDedicatedBandwidthAlongLink(): "+this+": Cannot allocate requested Bw("+this.getRequestedBandwidth()+"). Allocate only "
////					+requestedBandwidth);
////		}
//		double lowestLinkBwShared = Double.POSITIVE_INFINITY;
//		// Find the minimum bandwidth of Channel
//		/* 依次计算每条link可以给本channel的带宽（linkBW/number），取最小值 */
//		for(int i=0; i<nodes.size()-1; i++) {
//			Node from = nodes.get(i);
//			Link link = links.get(i);
//			double link_bw = link.getBw();
//			int numChannels = link.getChannelCount(from);
//
//			double link_bw_per_channel = link_bw / numChannels;
//
//			if(lowestLinkBwShared > link_bw_per_channel)
//				lowestLinkBwShared = link_bw_per_channel;
//		}
//
//		// Dedicated channel.
////		double channelBnadwidth = requestedBandwidth;
////		if(allocateMoreAvailableBw && (requestedBandwidth < lowestLinkBwShared) ) {
////			channelBnadwidth = lowestLinkBwShared;	// Give more BW if available.
////		}
//
//		double channelBnadwidth = lowestLinkBwShared;
//		// 该函数仅作用于以太网络
//		if(this.allocatedBandwidth != channelBnadwidth && this.wirelessLevel!=1 && wirelessLevel!=2) {
//			changeBandwidth(channelBnadwidth);
//			return true;
//		}
//
//		return false;
//	}
	public boolean adjustSharedBandwidthAlongLink() {
		if(chId != -1 && chId != 2999)
			return false;

		// step4:当有channel被回收或新的channel被创建时，都会动态地调整channel带宽
		double lowestLinkBw = getLowestSharedBandwidth();
		if(lowestLinkBw <= 0 )
		{
			throw new RuntimeException("Allocated bandwidth negative!!" + this + ", lowestLinkBw="+lowestLinkBw);
		}
		// 该函数仅作用于以太网络
		if(this.allocatedBandwidth != lowestLinkBw && this.wirelessLevel!=1 && wirelessLevel!=2) {
			changeBandwidth(lowestLinkBw);
			return true;
		}
		return false;
	}

	public boolean changeBandwidth(double newBandwidth){
		if (newBandwidth == allocatedBandwidth)
			return false; //nothing changed
		double timenow = CloudSim.clock();
//		boolean isChanged = this.updatePacketProcessing();
		this.allocatedBandwidth = newBandwidth;

		if(this.allocatedBandwidth == Double.NEGATIVE_INFINITY || this.allocatedBandwidth == Double.POSITIVE_INFINITY)
		{
			throw new RuntimeException("Allocated bandwidth infinity!!"+this);
		}

		if(this.allocatedBandwidth <= 0 )
		{
			throw new RuntimeException("Allocated bandwidth negative!!"+this);
		}

		return true;
	}

	public double getAllocatedBandwidth() {
		return allocatedBandwidth;
	}

	public int getActiveTransmissionNum() {
		return packetScheduler.getInTransmissionNum();
	}

	/**
	 * Updates processing of transmissions taking place in this Channel.
	 * @param currentTime current simulation time (in seconds)
	 * @return true if any tranmission has completed in this time, false if none is completed.
	 */
	/*
	public boolean updatePacketProcessing(){
		double currentTime = CloudSim.clock();
		double timeSpent = currentTime - this.previousTime;//NetworkOperatingSystem.round(currentTime - this.previousTime);

		if(timeSpent <= 0 || inTransmission.size() == 0)
			return false;	// Nothing changed

		//update the amount of transmission
		long processedThisRound =  Math.round(timeSpent*getAllocatedBandwidthPerTransmission());
		long processedTotal = processedThisRound*inTransmission.size();

		this.increaseProcessedBytes(processedTotal);

		//update transmission table; remove finished transmission
		LinkedList<Transmission> completedTransmissions = new LinkedList<Transmission>();
		for(Transmission transmission: inTransmission){
			transmission.addCompletedLength(processedThisRound);

			if (transmission.isCompleted()){
				completedTransmissions.add(transmission);
				//this.completed.add(transmission);
			}
		}

		this.completed.addAll(completedTransmissions);
		this.inTransmission.removeAll(completedTransmissions);
		previousTime=currentTime;

		Log.printLine(CloudSim.clock() + ": Channel.updatePacketProcessing() ("+this.toString()+"):Time spent:"+timeSpent+
				", BW/host:"+getAllocatedBandwidthPerTransmission()+", Processed:"+processedThisRound);

		if(completedTransmissions.isEmpty())
			return false;	// Nothing changed
		return true;
	}

	// Estimated finish time of one transmission
	private double estimateFinishTime(Transmission t) {
		double bw = getAllocatedBandwidthPerTransmission();

		if(bw == 0) {
			return Double.POSITIVE_INFINITY;
		}

		double eft= (double)t.getSize()/bw;
		return eft;
	}

	// The earliest finish time among all transmissions in this channel
	public double nextFinishTime() {
		//now, predicts delay to next transmission completion
		double delay = Double.POSITIVE_INFINITY;

		for (Transmission transmission:this.inTransmission){
			double eft = estimateFinishTime(transmission);
			if (eft<delay)
				delay = eft;
		}

		if(delay == Double.POSITIVE_INFINITY) {
			return delay;
		}
		else if(delay < 0) {
			throw new IllegalArgumentException("Channel.nextFinishTime: delay"+delay);
		}
		return delay;
	}
	*/

	/**
	 * Adds a new Transmission to be submitted via this Channel
	 * @param transmission transmission initiating
	 * @return estimated delay to complete this transmission
	 *
	 */
	/*
	public double addTransmission(Transmission transmission){
		if (this.inTransmission.isEmpty())
			previousTime=CloudSim.clock();

		this.inTransmission.add(transmission);
		double eft = packetScheduler.estimateFinishTime(transmission);
		return eft;
	}
	*/

	/**
	 * Remove a transmission submitted to this Channel
	 * @param transmission to be removed
	 *
	 */
	/*
	public void removeTransmission(Transmission transmission){
		inTransmission.remove(transmission);
	}
	*/

	/**
	 * @return list of Packets whose transmission finished, or empty
	 *         list if no packet arrived.
	 */
	/*
	public LinkedList<Transmission> getArrivedPackets(){
		LinkedList<Transmission> returnList = new LinkedList<Transmission>();

		if (!completed.isEmpty()){
			returnList.addAll(completed);
		}
		completed.reset();

		return returnList;
	}
	*/

	public int getChId() {
		return chId;
	}

	public double getLastUpdateTime(){
		return previousTime;
	}

	public String toString() {
		return "Channel("+this.srcId+"->"+this.dstId+"|"+this.chId
				+"): BW:"+allocatedBandwidth+", Transmissions:"+this.getActiveTransmissionNum();
	}

	public Node getLastNode() {
		Node node = this.nodes.get(this.nodes.size()-1);
		return node;
	}

	public int getSrcId() {
		return srcId;
	}

	public int getDstId() {
		return dstId;
	}

	public double getBandwidthBackup() {
		return BandwidthBackup; // default: 0
	}

//	public void updateRequestedBandwidth(double requestedBandwidth) {
//		this.requestedBandwidth = requestedBandwidth;
//		updateLinks();
//	}

	// For monitor
	private MonitoringValues mv = new MonitoringValues(MonitoringValues.ValueType.DataRate_BytesPerSecond);
	private double monitoringProcessedBytes = 0;

//	public double updateMonitor(double logTime, double timeUnit) {
//		//long capacity = (long) (this.getBw() * timeUnit);
//		double processedBytes = monitoringProcessedBytes;
//
//		double dataRate = (double)monitoringProcessedBytes / timeUnit;
//		mv.add(dataRate, logTime);
//
//		monitoringProcessedBytes = 0;
//
//		//LogWriter log = LogWriter.getLogger("channel_bw_utilization.csv");
//		//log.printLine(this+","+logTime+","+dataRate);
//
//		return processedBytes;
//	}

	public MonitoringValues getMonitoringValuesLinkUtilization() {
		return mv;
	}

	private void increaseProcessedBytes(double processedThisRound) {
		this.monitoringProcessedBytes += processedThisRound;

		// Add processed bytes to each link.
		for(int i=0; i<nodes.size()-1; i++) {
			Node from = nodes.get(i);
			Link link = links.get(i);
			link.increaseProcessedBytes(from, processedThisRound);
		}

		// Add processed bytes to each VM
		srcVm.increaseProcessedBytes(processedThisRound);
	}

	public double getMonitoredUtilization(double startTime, double endTime) {
		return getMonitoringValuesLinkUtilization().getAverageValue(startTime, endTime);
	}

	public double getTotalLatency() {
		return this.totalLatency;
	}

	public double addTransmission(Transmission transmission){
		return packetScheduler.addTransmission(transmission);
	}

	public void removeTransmission(Transmission transmission){
		packetScheduler.removeTransmission(transmission);
	}

	public List<Transmission> getArrivedPackets(){
		List<Transmission> completed = packetScheduler.getCompletedTransmission();
		packetScheduler.resetCompletedTransmission();
		return completed;
	}

	public List<Transmission> getFailedPackets(){
		List<Transmission> timeout = packetScheduler.getTimedOutTransmission();
		packetScheduler.resetTimedOutTransmission();
		return timeout;
	}

	/**
	 * 若channel的带宽为0，返回Double.POSITIVE_INFINITY。
	 * 否则规定至少 0.001s，不足就补到 0.001
	 */
	public double nextFinishTime() {
		return packetScheduler.nextFinishTime();
	}

	/**
	 * 若 channel 中有包传输完成，return true
	 */
	public boolean updatePacketProcessing() {
		double timenow = CloudSim.clock();
		double processedBytes = packetScheduler.updatePacketProcessing();
		// TODO: 将传输的bytes数累积到link的成员变量里
		this.increaseProcessedBytes(processedBytes); // for monitoring
		if(packetScheduler.getCompletedTransmission().isEmpty()
				&& packetScheduler.getTimedOutTransmission().isEmpty())
			return false;	// Nothing changed
		return true;
	}

	public void proceedChannel() {
	}
}
