/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.sim.cloudsimsdn.sdn.physicalcomponents;

import org.sim.cloudsimsdn.*;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.core.CloudSimTags;
import org.sim.cloudsimsdn.core.SimEvent;
import org.sim.cloudsimsdn.sdn.*;
import org.sim.cloudsimsdn.sdn.nos.ChannelManager;
import org.sim.cloudsimsdn.sdn.nos.NetworkOperatingSystem;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.CoreSwitch;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.EdgeSwitch;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmAllocationPolicyPriorityFirst;
import org.sim.cloudsimsdn.sdn.virtualcomponents.Channel;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;
import org.sim.cloudsimsdn.sdn.workload.Activity;
import org.sim.cloudsimsdn.sdn.workload.Processing;
import org.sim.cloudsimsdn.sdn.workload.Request;
import org.sim.cloudsimsdn.sdn.workload.Transmission;

import java.util.HashMap;
import java.util.List;

/**
 * Extended class of Datacenter that supports processing SDN-specific events.
 * In addtion to the default Datacenter, it processes Request submission to VM,
 * and application deployment request.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNDatacenter extends Datacenter {
	private NetworkOperatingSystem nos;

	private HashMap<Integer,Request> requestsTable = new HashMap<Integer, Request>(); /* requestsTable存放cloudletID(processing任务)，以及完成之后需要发送的 request */
	private static HashMap<Integer,Datacenter> globalVmDatacenterMap = new HashMap<Integer, Datacenter>();

	private static boolean isMigrateEnabled = false;

	// For results
	public static int migrationCompleted = 0;
	public static int migrationAttempted = 0;

	public Node wirelessGateway;

	public SDNDatacenter(String name, DatacenterCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, NetworkOperatingSystem nos) throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

		this.nos=nos;

		//nos.init();
		if(vmAllocationPolicy instanceof VmAllocationPolicyPriorityFirst) {
			((VmAllocationPolicyPriorityFirst)vmAllocationPolicy).setTopology(nos.getPhysicalTopology());
		}
	}

	public static Datacenter findDatacenterGlobal(int vmId) {
		// Find a data center where the VM is placed
		return globalVmDatacenterMap.get(vmId);
	}

	public void addVm(Vm vm){
		getVmList().add(vm);
		if (vm.isBeingInstantiated()) vm.setBeingInstantiated(false);
		vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler().getAllocatedMipsForVm(vm));
	}

	@Override
	protected void processVmCreate(SimEvent ev, boolean ack) {
		processVmCreateEvent((SDNVm) ev.getData(), ack);
		if(ack) {
			Vm vm = (Vm)ev.getData();
			send(nos.getId(), 0/*CloudSim.getMinTimeBetweenEvents()*/, CloudSimTags.VM_CREATE_ACK, vm);
		}
	}

	protected boolean processVmCreateEvent(SDNVm vm, boolean ack) {
		boolean result = getVmAllocationPolicy().allocateHostForVm(vm);

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			send(vm.getUserId(), 0, CloudSimTags.VM_CREATE_ACK, data);
		}

		if (result) {
			globalVmDatacenterMap.put(vm.getId(), this);

			getVmList().add(vm);

			if (vm.isBeingInstantiated()) {
				vm.setBeingInstantiated(false);
			}

			vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
					.getAllocatedMipsForVm(vm));
		}

		return result;
	}

	protected boolean processVmCreateDynamic(SimEvent ev) {
		Object[] data = (Object[]) ev.getData();
		SDNVm vm = (SDNVm) data[0];
		NetworkOperatingSystem callbackNOS = (NetworkOperatingSystem) data[1];

		boolean result = processVmCreateEvent(vm, true);
		data[0] = vm;
		data[1] = result;
		send(callbackNOS.getId(), 0/*CloudSim.getMinTimeBetweenEvents()*/, CloudSimTagsSDN.SDN_VM_CREATE_DYNAMIC_ACK, data);

		return result;
	}

	@Override
	public void processOtherEvent(SimEvent ev){
		switch(ev.getTag()){
			case CloudSimTagsSDN.REQUEST_SUBMIT:
				processRequestSubmit((Request) ev.getData());
//				this.nos.updateBWMonitor(Configuration.monitoringTimeInterval);
				break;
			case CloudSimTagsSDN.SDN_PACKET_COMPLETE:
				processPacketCompleted((ChanAndTrans)ev.getData());
				break;
			case CloudSimTagsSDN.SDN_PACKET_FAILED:
				processPacketFailed((Packet)ev.getData());
				break;
			case CloudSimTagsSDN.SDN_ARRIVED_GATEWAY:
				PacketArrivedGateway((ChanAndTrans)ev.getData());
				break;
			case CloudSimTagsSDN.SDN_ARRIVED_INTERCLOUD:
				PacketArrivedIntercloud((ChanAndTrans)ev.getData());
				break;
			case CloudSimTagsSDN.SDN_ARRIVED_GATEWAY2:
				PacketAcrossArrivedGateway((ChanAndTrans)ev.getData());
//				this.nos.updateBWMonitor(Configuration.monitoringTimeInterval);
				break;
//			case CloudSimTagsSDN.SDN_HOST_SEND_DELAY:
//				processNextActivity((Request)ev.getData());
			default:
				System.out.println("Unknown event recevied by SdnDatacenter. Tag:"+ev.getTag());
		}
	}

	/**
	 * 网络包抵达发送方Gateway
	 */
	private void PacketArrivedGateway(ChanAndTrans data) {
		ChannelManager channelManager = nos.getChannelManager();
		Packet pkt = data.tr.getPacket();
		Channel originCh = data.chan;
		int src = pkt.getOrigin(); // 发送方虚机
		int dst = pkt.getDestination(); // 接收方虚机
		double timenow = CloudSim.clock();
		int flowId = pkt.getFlowId();
/**********************************  半双工  *************************************/
		if (CloudSim.HalfDuplex) {
			String backkey = CloudSim.wirelessScheduler.makeChanKey("net", this.getName());
			if (CloudSim.wirelessScheduler.ChanKeyExist(backkey)) {
				pkt.setPacketFinishTime(CloudSim.clock());
				processPacketFailed(pkt);
				return;
			}
		}
/*******************************************************************************************/
//		double wirelessBwUp = CloudSim.wirelessBw;
		String mydcname = this.getName();
		double wirelessBwUp = Double.parseDouble(String.valueOf(CloudSim.wirelesschan_bw.get(mydcname)) );
		Channel channel = channelManager.findChannel(src, dst, flowId+1000);
		timenow = CloudSim.clock();
		if(channel == null) {
			channel = new Channel(flowId + 1000, src, dst, originCh.nodesAll, originCh.linksAll, wirelessBwUp,
					(SDNVm) NetworkOperatingSystem.findVmGlobal(src), (SDNVm) NetworkOperatingSystem.findVmGlobal(dst), true, 1);
			if (channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return;
			}
		}
		channel.disableChannel();
		channelManager.addWirelessChannel(src, dst, flowId+1000, channel);
		Transmission tr = new Transmission(pkt);

		channel.addTransmission(tr);
		String chankey = CloudSim.wirelessScheduler.makeChanKey(this.getName(), "net");
		if (CloudSim.wirelessScheduler.ChanKeyExist(chankey)){
			CloudSim.wirelessScheduler.AddChannel(this.getName(),"net", channel);
		} else {
			CloudSim.wirelessScheduler.AddChannel(this.getName(), "net", channel);
			this.nos.sendWirelessTimeSlide(this.nos.getId(), chankey);
		}
		timenow = CloudSim.clock();

	}

	private void PacketArrivedIntercloud(ChanAndTrans data) {
		ChannelManager channelManager = nos.getChannelManager();
		Packet pkt = data.tr.getPacket();
		Channel originCh = data.chan;
		int src = pkt.getOrigin(); // 发送方虚机
		int dst = pkt.getDestination(); // 接收方虚机
		int flowId = pkt.getFlowId();
/**********************************  半双工  *************************************/
		if (CloudSim.HalfDuplex) {
			String backkey = CloudSim.wirelessScheduler.makeChanKey(SDNDatacenter.findDatacenterGlobal(dst).getName(), this.getName());
			if (CloudSim.wirelessScheduler.ChanKeyExist(backkey)) {
				pkt.setPacketFinishTime(CloudSim.clock());
				processPacketFailed(pkt);
				return;
			}
		}
/*******************************************************************************************/
//		double wirelessBwDown = CloudSim.wirelessBw;
		String mydcname = SDNDatacenter.findDatacenterGlobal(dst).getName();
		double wirelessBwDown = Double.parseDouble(String.valueOf(CloudSim.wirelesschan_bw.get(mydcname)) );
		Channel channel = channelManager.findChannel(src, dst, flowId+2000);
		if(channel == null) {
			channel = new Channel(flowId + 2000, src, dst, originCh.nodesAll, originCh.linksAll, wirelessBwDown,
					(SDNVm) NetworkOperatingSystem.findVmGlobal(src), (SDNVm) NetworkOperatingSystem.findVmGlobal(dst), true, 2);
			if (channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return;
			}
		}
		channel.disableChannel();
		channelManager.addWirelessChannel(src, dst, flowId+2000, channel);
		Transmission tr = new Transmission(pkt);
		channel.addTransmission(tr);

		String chankey = CloudSim.wirelessScheduler.makeChanKey(this.getName(), SDNDatacenter.findDatacenterGlobal(dst).getName());
		if (CloudSim.wirelessScheduler.ChanKeyExist(chankey)){
			CloudSim.wirelessScheduler.AddChannel(this.getName(),SDNDatacenter.findDatacenterGlobal(dst).getName(), channel);
		} else {
			CloudSim.wirelessScheduler.AddChannel(this.getName(), SDNDatacenter.findDatacenterGlobal(dst).getName(), channel);
			this.nos.sendWirelessTimeSlide(this.nos.getId(), chankey);
		}
	}

	private void PacketAcrossArrivedGateway(ChanAndTrans data) {
		ChannelManager channelManager = nos.getChannelManager();
		Packet pkt = data.tr.getPacket();
		Channel originCh = data.chan;
		int src = pkt.getOrigin(); // 发送方虚机
		int dst = pkt.getDestination(); // 接收方虚机
		int flowId = pkt.getFlowId();
		double ethernetBw = 100000; //TODO:需要重计算

		Channel channel = channelManager.findChannel(src, dst, flowId+3000);
		if(channel == null) {
			channel = new Channel(flowId + 3000, src, dst, originCh.nodesAll, originCh.linksAll, ethernetBw,
					(SDNVm) NetworkOperatingSystem.findVmGlobal(src), (SDNVm) NetworkOperatingSystem.findVmGlobal(dst), true, 3);
			if (channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return;
			}
			// 重计算以太网带宽
			List<Link> links = channel.links;
			List<Node> nodes = channel.nodes; //gateway开始
			ethernetBw = Double.POSITIVE_INFINITY;
			for (int i=0; i<links.size(); ++i){
				if(ethernetBw > links.get(i).getFreeBandwidth(nodes.get(i))) {
					ethernetBw = links.get(i).getFreeBandwidth(nodes.get(i));
				}
			}
			channel.BandwidthBackup = channel.allocatedBandwidth = ethernetBw;
		}
		channelManager.addChannel(src, dst, flowId+3000, channel);
		Transmission tr = new Transmission(pkt);
		channel.addTransmission(tr);
		this.nos.sendInternalEvent();
//		pkt.setPacketStartTime(pkt.getStartTime()/*CloudSim.clock()*/);
	}

	public void processUpdateProcessing() {
		updateCloudletProcessing(); // Force Processing - TRUE!
		checkCloudletCompletion();
	}

	protected void processCloudletSubmit(SimEvent ev, boolean ack) {

		// gets the Cloudlet object
		Cloudlet cl = (Cloudlet) ev.getData();

		// Clear out the processed data for the previous time slot before Cloudlet submitted
		updateCloudletProcessing();

		try {
			// checks whether this Cloudlet has finished or not
			if (cl.isFinished()) {
				String name = CloudSim.getEntityName(cl.getUserId());
				Log.printLine(getName() + ": Warning - Cloudlet #" + cl.getCloudletId() + " owned by " + name
						+ " is already completed/finished.");
				Log.printLine("Therefore, it is not being executed again");
				Log.printLine();

				// NOTE: If a Cloudlet has finished, then it won't be processed.
				// So, if ack is required, this method sends back a result.
				// If ack is not required, this method don't send back a result.
				// Hence, this might cause CloudSim to be hanged since waiting
				// for this Cloudlet back.
				if (ack) {
					int[] data = new int[3];
					data[0] = getId();
					data[1] = cl.getCloudletId();
					data[2] = CloudSimTags.FALSE;

					// unique tag = operation tag
					int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
					sendNow(cl.getUserId(), tag, data);
				}

				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

				return;
			}

			// process this Cloudlet to this CloudResource
			cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
					.getCostPerBw());

			int userId = cl.getUserId();
			int vmId = cl.getVmId();
			// time to transfer the files
			double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

			SDNHost host = (SDNHost)getVmAllocationPolicy().getHost(vmId, userId);
			Vm vm = host.getVm(vmId, userId);
			CloudletScheduler scheduler = vm.getCloudletScheduler();

			double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime); // This estimated time is useless

			//host.adjustMipsShare();
			//estimatedFinishTime = scheduler.getNextFinishTime(CloudSim.clock(), scheduler.getCurrentMipsShare());

			// Check the new estimated time by using host's update VM processing funciton.
			// This function is called only to check the next finish time
			double tmptime = CloudSim.clock();
			// cloudlet的预计执行时间
			estimatedFinishTime = host.updateVmsProcessing(CloudSim.clock());

			double estimatedFinishDelay = estimatedFinishTime - CloudSim.clock();
			//estimatedFinishTime -= CloudSim.clock();

			// if this cloudlet is in the exec queue
			//TODO:去掉cloudlet的estimatedFinishDelay范围判断、去掉执行MinTime
//			if (estimatedFinishDelay > 0.0 && estimatedFinishTime < Double.MAX_VALUE) {
				estimatedFinishTime += fileTransferTime;
				//Log.printLine(getName() + ".processCloudletSubmit(): " + "Cloudlet is going to be processed at: "+(estimatedFinishTime + CloudSim.clock()));

				// gurantees a minimal interval before scheduling the event
//				if (estimatedFinishDelay < CloudSim.getMinTimeBetweenEvents()) {
//					estimatedFinishDelay = CloudSim.getMinTimeBetweenEvents();
//				}
				estimatedFinishDelay = 0.0000012;// (弃用)TODO:自定义cloudlet延迟，这里可以设为交换时间
				// 加上主机发送带宽的延迟？？
				send(getId(), estimatedFinishDelay, CloudSimTags.VM_DATACENTER_EVENT);
//			}

			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cl.getCloudletId();
				data[2] = CloudSimTags.TRUE;

				// unique tag = operation tag
				int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
				sendNow(cl.getUserId(), tag, data);
			}
		} catch (ClassCastException c) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
			c.printStackTrace();
		} catch (Exception e) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error.");
			e.printStackTrace();
		}
		//TODO: CloudletSubmit时不要检查CloudletCompletion
//		checkCloudletCompletion();
	}

	@Override
	protected void checkCloudletCompletion() {
		if(!nos.isApplicationDeployed())
		{
			super.checkCloudletCompletion();
			return;
		}

		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {

				// Check all completed Cloudlets
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					double timenow = CloudSim.clock();
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {
						// For completed cloudlet -> process next activity.
						Request req = requestsTable.remove(cl.getCloudletId());
						req.getPrevActivity().setFinishTime(CloudSim.clock());

						if (req.isFinished()){
							// All requests are finished, no more activities to do. Return to user
							send(req.getUserId(), 0, CloudSimTagsSDN.REQUEST_COMPLETED, req);
						} else {
							//consume the next activity from request. It should be a transmission.
							processNextActivity(req);

//							Transmission tr = (Transmission) req.getNextActivity();
//							// 主机发送时延 10MB
//							BigDecimal delay = new BigDecimal(tr.getPacket().getSize()).divide(BigDecimal.valueOf(CloudSim.HostSendBw));
//							send(this.getId(), delay.doubleValue(), CloudSimTagsSDN.SDN_HOST_SEND_DELAY, req);
						}
					}
				}

				// Check all failed Cloudlets (time out)
				List<Cloudlet> failedCloudlet = ((CloudletSchedulerMonitor) (vm.getCloudletScheduler())).getFailedCloudlet();
				for(Cloudlet cl:failedCloudlet) {
					processCloudletFailed(cl);
				}
			}
		}
	}

	/**
	 * 交给processNextActivity处理活动
	 */
	private void processRequestSubmit(Request req) {
		Activity ac = req.getNextActivity();

		if(ac instanceof Processing) {
			processNextActivity(req);
		}
		else {
			System.err.println("Request should start with Processing!!");
		}
	}

	private void processCloudletFailed(Cloudlet cl) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processCloudletFailed(): Cloudlet failed: "+cl);

		Request req = requestsTable.remove(cl.getCloudletId());
		Activity prev = req.getPrevActivity();
		if(prev != null)
			prev.setFailedTime(CloudSim.clock()); // Set as finished.
		Activity next = req.getNextActivity();
		if(next != null)
			next.setFailedTime(CloudSim.clock()); // Set as finished.

		Request lastReq = req.getTerminalRequest();
		send(req.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTagsSDN.REQUEST_FAILED, lastReq);
	}


	private void processPacketFailed(Packet pkt) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processPacketFailed(): Packet failed: "+pkt);

		pkt.setPacketFailedTime(CloudSim.clock());
		Request req = pkt.getPayload();

		Request lastReq = req.getTerminalRequest(); //一般是自身
		send(req.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTagsSDN.REQUEST_FAILED, lastReq);
	}

	private void processPacketCompleted(ChanAndTrans ct) {
		// 以太网内部：包裹即将到达destHost
		double timenow = CloudSim.clock();
		Packet pkt = ct.tr.getPacket();
		int vmId = pkt.getDestination();
		Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
		//Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed: "+pkt +". Send to destination:"+ch.getLastNode());
		double tmplatency = 0;//ch.getTotalLatency();
		for(Node switch_ :ct.chan.nodesAll){ //TODO:在这里加上所有交换时延
			if(switch_ instanceof EdgeSwitch || switch_ instanceof CoreSwitch){
				if(switch_.getBandwidth() >= 100000000) //100G
					tmplatency += 0.1*0.000001; //0.1微秒
				else if(switch_.getBandwidth() >= 40000000)
					tmplatency += 0.2*0.000001;
				else
					tmplatency += 0.5*0.000001;
			}
		}

		pkt.setPacketFinishTime(CloudSim.clock());
		Request req = pkt.getPayload();
		req.switchTime = tmplatency;
		processNextActivity(req);
	}

	private void processNextActivity(Request req) {
		Activity ac = req.removeNextActivity();
		if(req.activities.size() == 1) {
			ac = req.removeNextActivity();//不涉及cloudlet，仅仿真网络时延
		}
		ac.setStartTime(CloudSim.clock());

		if(ac instanceof Transmission) {
			processNextActivityTransmission((Transmission)ac);
		}
		else if(ac instanceof Processing) {
			//Processing 是结尾符号，表示消息传输已结束，需要被接收方处理
			sendNow(req.getUserId(), CloudSimTagsSDN.REQUEST_COMPLETED, req);
		}
	}

	private void processNextActivityTransmission(Transmission tr) {
		Packet pkt = tr.getPacket();

		//send package to router via channel (NOS)
		pkt = nos.addPacketToChannel(pkt);
		pkt.setPacketStartTime(CloudSim.clock());
		tr.setRequestedBW(nos.getBandwidthBackup(pkt));
	}

	/**
	 * 在 dc的 requestsTable中添加 紧跟此计算负载之后 需发送的request
	 * 向自己 send cloudlet任务
	 * processing.setVmMipsPerPE(vm.mips);
	 */
	private void processNextActivityProcessing(Processing proc, Request reqAfterCloudlet) {
		Cloudlet cl = proc.getCloudlet();
		proc.clearCloudlet();

		requestsTable.put(cl.getCloudletId(), reqAfterCloudlet);
		double timenow = CloudSim.clock();
		sendNow(getId(), CloudSimTags.CLOUDLET_SUBMIT, cl);

		// Set the requested MIPS for this cloudlet.
		int userId = cl.getUserId();
		int vmId = cl.getVmId();

		Host host = getVmAllocationPolicy().getHost(vmId, userId);
		if(host == null) {
			throw new NullPointerException("Error! cannot find a host for Workload:"+ proc+". VM="+vmId);
		}
		Vm vm = host.getVm(vmId, userId);
		double mips = vm.getMips();
		proc.setVmMipsPerPE(mips);
	}

	public void printDebug() {
		System.err.println(CloudSim.clock()+": # of currently processing Cloudlets: "+this.requestsTable.size());
	}

	public NetworkOperatingSystem getNOS() {
		return this.nos;
	}

	public String toString() {
		return "SDNDataCenter:(NOS="+nos.toString()+")";
	}
}
