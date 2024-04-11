/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.nos;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.sim.cloudsimsdn.*;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.core.CloudSimTags;
import org.sim.cloudsimsdn.core.SimEntity;
import org.sim.cloudsimsdn.core.SimEvent;
import org.sim.cloudsimsdn.core.predicates.PredicateType;
import org.sim.cloudsimsdn.sdn.*;
import org.sim.cloudsimsdn.sdn.physicalcomponents.*;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.CoreSwitch;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.EdgeSwitch;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.Switch;
import org.sim.cloudsimsdn.sdn.policies.selectlink.LinkSelectionPolicy;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.overbooking.OverbookingVmAllocationPolicy;
import org.sim.cloudsimsdn.sdn.virtualcomponents.Channel;
import org.sim.cloudsimsdn.sdn.virtualcomponents.FlowConfig;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;
import org.sim.cloudsimsdn.sdn.virtualcomponents.VirtualNetworkMapper;
import org.sim.cloudsimsdn.sdn.workload.Transmission;

import java.security.cert.TrustAnchor;
import java.util.*;

/**
 * NOS calculates and estimates network behaviour. It also mimics SDN Controller functions.
 * It manages channels between allSwitches, and assigns packages to channels and control their completion
 * Once the transmission is completed, forward the packet to the destination.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public abstract class NetworkOperatingSystem extends SimEntity {
	protected SDNDatacenter datacenter;

	// Physical topology
	protected PhysicalTopology topology;

	// Virtual topology
	protected VirtualNetworkMapper vnMapper = null;
	protected ChannelManager channelManager = null;
	protected boolean isApplicationDeployed = false;

	// Map: Vm ID -> VM
	protected HashMap<Integer, Vm> vmMapId2Vm = new HashMap<Integer, Vm>();

	// Global map (static): Vm ID -> VM
	protected static HashMap<Integer, Vm> gvmMapId2Vm = new HashMap<Integer, Vm>();

	// Vm ID (src or dst) -> all Flow from/to the VM
	protected Multimap<Integer, FlowConfig> flowMapVmId2Flow = HashMultimap.create();

	// Global map (static): Flow ID -> VM
	protected static Map<Integer, FlowConfig> gFlowMapFlowId2Flow = new HashMap<Integer, FlowConfig>();

	// Resolution of the result.
	public static final long bandwidthWithinSameHost = 100000000; // bandwidth between VMs within a same host: 12Gbps = 1.5GBytes/sec
	public static final double latencyWithinSameHost = 0.0; //0.1 msec latency

	private double lastMigration = 0;
	public double lastAdjustAllChannelTime = -1;
	private double nextEventTime = -1;

	public ChannelManager getChannelManager(){
		return this.channelManager;
	}
	/**
	 * 1. map VMs and middleboxes to hosts, add the new vm/mb to the vmHostTable, advise host, advise dc
	 * 2. set channels and bws
	 * 3. set routing tables to restrict hops to meet latency
	 */
	protected abstract boolean deployApplication(List<Vm> vms, Collection<FlowConfig> links);

	public NetworkOperatingSystem(String name) {
		super(name);

		this.vnMapper = new VirtualNetworkMapper(this);
		this.channelManager = new ChannelManager(this, vnMapper);

		this.topology = new PhysicalTopologyInterCloud();
	}

	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
		vnMapper.setLinkSelectionPolicy(linkSelectionPolicy);
	}

	public void configurePhysicalTopology(Collection<SDNHost> hosts, Collection<Switch> switches, Collection<Link> links) {
		for(SDNHost sdnHost: hosts) {
			topology.addNode(sdnHost);
		}

		for(Switch sw:switches) {
			topology.addNode(sw);
		}

		for(Link link:links) {
			topology.addLink(link);
		}

		topology.buildDefaultRouting();
	}

	@Override
	public void startEntity() { //TODO:这里开始 “monitor”
//		send(this.getId(), Configuration.monitoringTimeInterval, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
		send(this.getId(), Configuration.monitoringTimeInterval, CloudSimTagsSDN.MONITOR_BW_UTILIZATION);
	}

	@Override
	public void shutdownEntity() {

	}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();

		switch(tag){
			case CloudSimTagsSDN.SDN_INTERNAL_CHANNEL_PROCESS:
				processInternalAdjustChannels();
				break;
			case CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS:
				processInternalPacketProcessing();
				break;
			/* **************************************************************/
			case CloudSimTagsSDN.SDN_WIRELESS_TIMESLIDE:
				processWirelessTimeSlot((String)ev.getData());
				break;
			/* **************************************************************/
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreateAck(ev);
				break;
			case CloudSimTags.VM_DESTROY:
				processVmDestroyAck(ev);
				break;
			case CloudSimTagsSDN.MONITOR_BW_UTILIZATION:
				double timenow = CloudSim.clock();
				double highestLinkUtilThisUnit = this.updateBWMonitor(Configuration.monitoringTimeInterval);
				Set<Integer> excludetags = new HashSet<Integer>();
				excludetags.add(CloudSimTagsSDN.MONITOR_BW_UTILIZATION);
				excludetags.add(CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
				if(CloudSimEx.hasMoreEvent(excludetags)){
//					double nextEventDelay = CloudSimEx.getNextEventTime() - CloudSim.clock();
//					double nextMonitorDelay = Configuration.monitoringTimeInterval;
//					if(nextEventDelay > Configuration.monitoringTimeInterval) {
//						nextMonitorDelay = nextEventDelay+Configuration.monitoringTimeInterval;
//					}
					send(this.getId(), Configuration.monitoringTimeInterval, CloudSimTagsSDN.MONITOR_BW_UTILIZATION);
				}

			case CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION:
				//TODO: 尝试删除MONITOR_UPDATE_UTILIZATION?
//				if(this.datacenter != null)
//					this.datacenter.processUpdateProcessing();
//				channelManager.updatePacketProcessing();
//				excludetags = new HashSet<Integer>() ;
//				excludetags.add(CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
//				if(CloudSimEx.hasMoreEvent(excludetags)){
//					double nextMonitorDelay = Configuration.monitoringTimeInterval;
//					double nextEventDelay = CloudSimEx.getNextEventTime() - CloudSim.clock();
//
//					// If there's no event between now and the next monitoring time, skip monitoring until the next event time.
//					if(nextEventDelay > nextMonitorDelay) {
//						nextMonitorDelay = nextEventDelay;
//					}
//					send(this.getId(), nextMonitorDelay, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
//				}
				break;
			default: System.out.println("Unknown event received by "+super.getName()+". Tag:"+ev.getTag());
		}
	}

	protected void processVmCreateAck(SimEvent ev) {
		// override,见 NetworkOperatingSystemSimple
	}

	private void processInternalPacketProcessing() {
		double timenow = CloudSim.clock();
		if(channelManager.updatePacketProcessing()) {
			sendInternalEvent();
		}
	}

	protected void processVmDestroyAck(SimEvent ev) {
		Vm destroyedVm = (Vm) ev.getData();
		// remove all channels transferring data from or to this vm.
		for(Vm vm:this.vmMapId2Vm.values()) {
			channelManager.removeChannel(vm.getId(), destroyedVm.getId(), -1);
			channelManager.removeChannel(destroyedVm.getId(), vm.getId(), -1);
		}
		sendInternalEvent();
	}

	protected void processInternalAdjustChannels() {
		channelManager.adjustAllChannel();
	}

	public boolean startDeployApplicatoin() {
		List<Vm> vms = new ArrayList<Vm>(vmMapId2Vm.values());
		boolean result = deployApplication(vms, this.flowMapVmId2Flow.values());
		isApplicationDeployed = result;
		return result;
	}

	public Packet addPacketToChannel(Packet orgPkt) {
		double timenow = CloudSim.clock();
		Packet pkt = orgPkt;
//		channelManager.updatePacketProcessing(); //TODO(尝试):???注释此行，添加包前不更新channel
		int src = pkt.getOrigin();
		String srchostname = ((SDNVm) NetworkOperatingSystem.findVmGlobal(src)).getHostName();
		int dst = pkt.getDestination();
		String dsthostname = ((SDNVm) NetworkOperatingSystem.findVmGlobal(dst)).getHostName();
		int flowId = pkt.getFlowId();
//		System.out.println("消息【id:"+pkt.getPacketId()+"】【src:"+findHost(src)+"】->【dst:"+findHost(dst)+"】");

		Channel channel = channelManager.findChannel(src, dst, flowId);
		if(channel == null) {
			//No channel established. Create a new channel.
			SDNHost sender = findHost(src);
			channel = channelManager.createChannel(src, dst, flowId, sender);

			if(channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return pkt;
			}
			channelManager.addChannel(src, dst, flowId, channel);
		}

		channel.addTransmission(new Transmission(pkt));
//		Log.printLine(CloudSim.clock() + ": " + getName() + ".addPacketToChannel ("+channel
//				+"): Transmission added:" +
//				NetworkOperatingSystem.getVmName(src) + "->"+
//				NetworkOperatingSystem.getVmName(dst) + ", flow ="+flowId + " / eft="+eft);

		sendInternalEvent();

		return pkt;
	}

	/**
	 * 有 channel 传输包完毕。发送SDN-101给发包dc / SDN-1给收包dc。
	 */
	public void processCompletePackets(List<Channel> channels){
		for(Channel ch:channels) {
			for (Transmission tr:ch.getArrivedPackets()){
/* **********************************************************************/
				if(ch.isWireless && ch.wirelessLevel == 0){ // 包即将抵达Gateway，新建wirelessUpChan(gateway->intercloud)并addTransmission
					double delay = ch.getTotalLatency(); // 有线部分的物理链路延迟(srchost->gateway)
					send(this.datacenter.getId(), 0, CloudSimTagsSDN.SDN_ARRIVED_GATEWAY, new ChanAndTrans(ch, tr));
					continue; // 在上一层 caller 会删除空闲 channel
				}
				if(ch.isWireless && ch.wirelessLevel == 1){ // 包即将抵达intercloud(wirelessnet)，给netDC发消息，新建wirelessDownChan(intercloud->gateway)并addTransmission
					double delay = ch.getTotalLatency(); // gateway->intercloud的延迟
					send(CloudSim.getEntityId("net"),0 , CloudSimTagsSDN.SDN_ARRIVED_INTERCLOUD, new ChanAndTrans(ch, tr));
					continue; // 在上一层 caller 会删除空闲 channel
				}
				if(ch.isWireless && ch.wirelessLevel == 2){ // 包即将跨平台抵达Gateway，新建EthernetChan(gateway->desthost)并addTransmission
					double delay = ch.getTotalLatency(); // intercloud->gateway的延迟
					Packet pkt = tr.getPacket();
					int vmId = pkt.getDestination();
					Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
					send(dc.getId(), 0, CloudSimTagsSDN.SDN_ARRIVED_GATEWAY2, new ChanAndTrans(ch, tr));
					continue; // 在上一层 caller 会删除空闲 channel
				}
/* **********************************************************************/
				// 以太网内部：包裹即将到达destHost
				double timenow = CloudSim.clock();
				Packet pkt = tr.getPacket();
				int vmId = pkt.getDestination();
				Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
//				//Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed: "+pkt +". Send to destination:"+ch.getLastNode());
//				double tmplatency = 0;//ch.getTotalLatency();
//				for(Node switch_ :ch.nodesAll){ //TODO:在这里加上所有交换时延
//					if(switch_ instanceof EdgeSwitch || switch_ instanceof CoreSwitch){
//						if(switch_.getBandwidth() >= 100000000) //100G
//							tmplatency += 0.1*0.000001; //0.1微秒
//						else if(switch_.getBandwidth() >= 40000000)
//							tmplatency += 0.2*0.000001;
//						else
//							tmplatency += 0.5*0.000001;
//					}
//				}
				ChanAndTrans ct = new ChanAndTrans(ch, tr);
				sendPacketCompleteEvent(dc, ct, 0);
			}

			for (Transmission tr:ch.getFailedPackets()){
				Packet pkt = tr.getPacket();
				sendPacketFailedEvent(this.datacenter, pkt, ch.getTotalLatency());
			}
		}
	}

	private void sendPacketCompleteEvent(Datacenter dc, ChanAndTrans ct, double latency){
		send(dc.getId(), latency, CloudSimTagsSDN.SDN_PACKET_COMPLETE, ct);
	}

	private void sendPacketFailedEvent(Datacenter dc, Packet pkt, double latency){
		send(dc.getId(), latency, CloudSimTagsSDN.SDN_PACKET_FAILED, pkt);
	}

//	public void sendAdjustAllChannelEvent() {
//		if(CloudSim.clock() != lastAdjustAllChannelTime) {
//			send(getId(), 0, CloudSimTagsSDN.SDN_INTERNAL_CHANNEL_PROCESS);
//			lastAdjustAllChannelTime = CloudSim.clock();
//		}
//	}

	private void processWirelessTimeSlot(String chankey) {
		List<Channel> list = CloudSim.wirelessScheduler.GetChanList(chankey);
		if(list == null || list.size() == 0){
			// 不再继续该chankey对应的 timeslot
			return;
		}
		if (list.size() == 1){
			int a = 0;
		}
		double timenow = CloudSim.clock();
		CloudSim.wirelessScheduler.PushBackAndDisableOthers(chankey);

		channelManager.updatePacketProcessing(); // 此步会处理 processCompletePackets
		// 继续该chankey对应的 timeslot
		sendWirelessTimeSlide(this.getId(), chankey);
	}

	/**
	 * 计算nextFinishTime时间，发 SDN-3 号消息(SDN_INTERNAL_PACKET_PROCESS)
	 */
	public void sendInternalEvent() {
		if(channelManager.getTotalChannelNum() != 0) {
			if(nextEventTime == CloudSim.clock() + CloudSim.getMinTimeBetweenEvents())
				return;

			// More to process. Send event again
			double delay = channelManager.nextFinishTime();
/* ********************************************************************************************/
			if(delay == Double.POSITIVE_INFINITY) {// channel都被disable了
				return;
			}
/* ********************************************************************************************/
			//TODO:处理delay==0。同个netOS内，有其他包的到达顺带通知netOS自己也到达了。不必重复通知？
			if (delay < CloudSim.getMinTimeBetweenEvents()) {
				//Log.printLine(CloudSim.clock() + ":Channel: delay is too short: "+ delay);
				delay = CloudSim.getMinTimeBetweenEvents();
			}

			//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): delay for next event="+ delay);

			if((nextEventTime > CloudSim.clock() + delay) || nextEventTime <= CloudSim.clock() )
			{
				//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): next event time changed! old="+ nextEventTime+", new="+(CloudSim.clock()+delay));

				CloudSim.cancelAll(getId(), new PredicateType(CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS));
				send(this.getId(), delay, CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS);
				nextEventTime = CloudSim.clock()+delay;
			}
		}
	}

	public void sendWirelessTimeSlide(int destNOSID, String chankey) {
		send(destNOSID, 0.000001, CloudSimTagsSDN.SDN_WIRELESS_TIMESLIDE, chankey);// TODO:TDMA时间片 1 微妙
	}

	public long getBandwidthBackup(int flowId) {
		FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
		if(flow != null)
			return flow.getBw();

		return 0L;
	}

	public double getBandwidthBackup(Packet pkt) {
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
		Channel channel=channelManager.findChannel(src, dst, flowId);
		double bw = channel.getBandwidthBackup();

		return bw;
	}

//	public void updateBandwidthFlow(int srcVm, int dstVm, int flowId, long newBw) {
//		if(flowId == -1) {
//			return;
//		}
//
//		FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
//		flow.updateReqiredBandwidth(newBw);
//	}

	public void setDatacenter(SDNDatacenter dc) {
		this.datacenter = dc;
	}

	@Override
	public String toString() {
		return "NOS:"+getName();
	}

	public static Map<String, Integer> getVmNameToIdMap() {
		Map<String, Integer> map = new HashMap<>();
		for(Vm vm:gvmMapId2Vm.values()) {
			SDNVm svm = (SDNVm)vm;
			map.put(svm.getName(), svm.getId());
		}

		return map;
	}

	public static Map<String, Integer> getFlowNameToIdMap() {
		Map<String, Integer> map = new HashMap<String, Integer>();
		for(FlowConfig flow:gFlowMapFlowId2Flow.values()) {
			map.put(flow.getName(), flow.getFlowId());
		}

		map.put("default", -1);

		return map;
	}

	public PhysicalTopology getPhysicalTopology() {
		return this.topology;
	}

	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>)topology.getAllHosts();
	}

	public List<Switch> getSwitchList() {
		return (List<Switch>) topology.getAllSwitches();
	}

	public boolean isApplicationDeployed() {
		return isApplicationDeployed;
	}

	public Vm findVmLocal(int vmId) {
		return vmMapId2Vm.get(vmId);
	}

	public static String getVmName(int vmId) {
		SDNVm vm = (SDNVm) gvmMapId2Vm.get(vmId);
		return vm.getName();
	}

	public static Vm findVmGlobal(int vmId) {
		return gvmMapId2Vm.get(vmId);
	}

	public SDNHost findHost(int vmId) {
		Vm vm = findVmLocal(vmId);
		if(vm != null) {
			// VM is in this NOS (datacenter)
			return (SDNHost)this.datacenter.getVmAllocationPolicy().getHost(vm);
		}

		// VM is in another data center. Find the host!
		vm = findVmGlobal(vmId);
		if(vm != null) {
			Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
			if(dc != null)
				return (SDNHost)dc.getVmAllocationPolicy().getHost(vm);
		}

		return null;
	}

	public void addVm(SDNVm vm) {
		vmMapId2Vm.put(vm.getId(), vm);
		gvmMapId2Vm.put(vm.getId(), vm);
	}

	private void insertFlowToMap(FlowConfig flow) {
		flowMapVmId2Flow.put(flow.getSrcId(), flow);
		flowMapVmId2Flow.put(flow.getDstId(), flow);
	}

	public void addFlow(FlowConfig flow) {
		insertFlowToMap(flow);

//		if(flow.getFlowId() != -1) {
//			gFlowMapFlowId2Flow.put(flow.getFlowId(), flow);
//		}
	}

	// for monitoring
	public double updateBWMonitor(double monitoringTimeUnit) {
		double highest=0;
		// Update utilization of all links
		Set<Link> links = new HashSet<Link>(this.topology.getAllLinks());
		for(Link l:links) {
			double util = l.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
			if(util > highest) highest=util;
		}
		//System.err.println(CloudSim.clock()+": Highest utilization of Links = "+highest);

//		channelManager.updateMonitor(monitoringTimeUnit);
		return highest;
	}

	private void updateHostMonitor(double monitoringTimeUnit) {
		if(datacenter != null)
			for(SDNHost h: datacenter.<SDNHost>getHostList()) {
				h.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
			}
	}

	private void updateSwitchMonitor(double monitoringTimeUnit) {
		for(Switch s:getSwitchList()) {
			s.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
		}
	}

	private void updateVmMonitor(double logTime) {
		if(datacenter == null)
			return;

		VmAllocationPolicy vmAlloc = datacenter.getVmAllocationPolicy();
		if(vmAlloc instanceof OverbookingVmAllocationPolicy) {
			for(Vm v: this.vmMapId2Vm.values()) {
				SDNVm vm = (SDNVm)v;
				double mipsOBR = ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioMips((SDNVm) vm);
				LogWriter log = LogWriter.getLogger("vm_OBR_mips.csv");
				log.printLine(vm.getName()+","+logTime+","+mipsOBR);

				double bwOBR =  ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioBw((SDNVm) vm);
				log = LogWriter.getLogger("vm_OBR_bw.csv");
				log.printLine(vm.getName()+","+logTime+","+bwOBR);
			}
		}
	}

//	public Vm getSFForwarderOriginalVm(int vmId) {
//		return this.sfcForwarder.getOriginalSF(vmId);
//	}

	public double calculateLatency(int srcVmId, int dstVmId, int flowId) {
		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		Node srcHost = findHost(srcVmId);
		vnMapper.buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, nodes, links);

		double latency = 0;
		// Calculate the latency of the links.
		for(Link l:links) {
			latency += l.getLatencyInSeconds();
		}

		return latency;
	}

	/*
	protected void debugPrintMonitoredValues() {
		//////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////
		// For debug only

		Collection<Link> links = this.topology.getAllLinks();
		for(Link l:links) {
			System.err.println(l);
			MonitoringValues mv = l.getMonitoringValuesLinkUtilizationUp();
			System.err.print(mv);
			mv = l.getMonitoringValuesLinkUtilizationDown();
			System.err.print(mv);
		}
//
//		for(Channel ch:this.allChannels) {
//			System.err.println(ch);
//			MonitoringValues mv = ch.getMonitoringValuesLinkUtilization();
//			System.err.print(mv);
//		}

		for(SDNHost h:datacenter.<SDNHost>getHostList()) {
			System.err.println(h);
			MonitoringValues mv = h.getMonitoringValuesHostCPUUtilization();
			System.err.print(mv);
		}

		for(Vm vm:vmMapId2Vm.values()) {
			SDNVm tvm = (SDNVm)vm;
			System.err.println(tvm);
			MonitoringValues mv = tvm.getMonitoringValuesVmCPUUtilization();
			System.err.print(mv);
		}
	}
	*/
}
