/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.Log;
import org.sim.cloudsimsdn.UtilizationModelFull;
import org.sim.cloudsimsdn.Vm;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.core.CloudSimTags;
import org.sim.cloudsimsdn.core.SimEntity;
import org.sim.cloudsimsdn.core.SimEvent;
import org.sim.cloudsimsdn.sdn.nos.NetworkOperatingSystem;
import org.sim.cloudsimsdn.sdn.parsers.VirtualTopologyParser;
import org.sim.cloudsimsdn.sdn.parsers.WorkloadParser;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNDatacenter;
import org.sim.cloudsimsdn.sdn.virtualcomponents.FlowConfig;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;
import org.sim.cloudsimsdn.sdn.workload.Request;
import org.sim.cloudsimsdn.sdn.workload.Workload;
import org.sim.cloudsimsdn.sdn.workload.WorkloadResultWriter;

import java.util.*;

/**
 * Broker class for CloudSimSDN example. This class represents a broker (Service Provider)
 * who uses the Cloud data center.
 *
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SDNBroker extends SimEntity {

	public static double experimentStartTime = -1;
	public static double experimentFinishTime = Double.POSITIVE_INFINITY;

	public static int lastAppId = 0;

	private static Map<String, SDNDatacenter> datacenters = new HashMap<String, SDNDatacenter>();
	private static Map<Integer, SDNDatacenter> vmIdToDc = new HashMap<Integer, SDNDatacenter>();

	private String applicationFileName = null;
	private HashMap<WorkloadParser, Integer> workloadId=null;
	private HashMap<Long, Workload> requestMap=null;
	private List<String> workloadFileNames=null;

	public SDNBroker(String name) throws Exception {
		super(name);
		this.workloadFileNames = new ArrayList<String>();
		workloadId = new HashMap<WorkloadParser, Integer>();
		requestMap = new HashMap<Long, Workload>();
	}

	@Override
	public void startEntity() {
		sendNow(getId(), CloudSimTagsSDN.APPLICATION_SUBMIT, this.applicationFileName);
	}
	@Override
	public void shutdownEntity() {
		for(SDNDatacenter datacenter:datacenters.values()) {
			List<Vm> vmList = datacenter.getVmList();
//			for(Vm vm:vmList) {
//				Log.printLine(CloudSim.clock() + ": " + getName() + ": Shuttingdown.. VM:" + vm.getId());
//			}
		}
	}
	public List<Workload> printResult() {
		int numWorkloads=0, numWorkloadsCPU=0, numWorkloadsNetwork =0,
				numWorkloadsOver=0, numWorkloadsNetworkOver=0, numWorkloadsCPUOver=0, numTimeout=0;
		double totalServetime=0, totalServetimeCPU=0, totalServetimeNetwork=0;

		// For group analysis
		int[] groupNumWorkloads = new int[SDNBroker.lastAppId];
		double[] groupTotalServetime = new double[SDNBroker.lastAppId];
		double[] groupTotalServetimeCPU = new double[SDNBroker.lastAppId];
		double[] groupTotalServetimeNetwork = new double[SDNBroker.lastAppId];
		List<Workload> wlsList = new ArrayList<>();
		double maxServetime = 0.0;
		for(WorkloadParser wp:workloadId.keySet()) {
			WorkloadResultWriter wrw = wp.getResultWriter();
			wlsList.addAll(wrw.printStatistics()); //TODO:这里打印workloads
			numWorkloads += wrw.getWorklaodNum();
			numTimeout +=  wrw.getTimeoutNum();
			numWorkloadsOver += wrw.getWorklaodNumOvertime();
			numWorkloadsCPU += wrw.getWorklaodNumCPU();
			numWorkloadsCPUOver += wrw.getWorklaodNumCPUOvertime();
			numWorkloadsNetwork += wrw.getWorklaodNumNetwork();
			numWorkloadsNetworkOver += wrw.getWorklaodNumNetworkOvertime();

			totalServetime += wrw.getServeTime();
			totalServetimeCPU += wrw.getServeTimeCPU();
			totalServetimeNetwork += wrw.getServeTimeNetwork();
			maxServetime =  wrw.getMaxPerTime();
			// For group analysis
			groupNumWorkloads[wp.getGroupId()] += wrw.getWorklaodNum();
			groupTotalServetime[wp.getGroupId()] += wrw.getServeTime();
			groupTotalServetimeCPU[wp.getGroupId()] += wrw.getServeTimeCPU();
			groupTotalServetimeNetwork[wp.getGroupId()] += wrw.getServeTimeNetwork();
		}
		Log.printLine("================ latency =================");
		Log.printLine("消息总数: "+ numWorkloads);
//		Log.printLine("消息总延迟: "+ totalServetimeNetwork);
		if(numWorkloads!=0) {
			Log.printLine("消息平均延迟: "+ totalServetime/numWorkloads);
			Log.printLine("消息最大延迟: "+ maxServetime);
		}
		return wlsList;
	}

	public void submitDeployApplication(SDNDatacenter dc, String filename) {
		SDNBroker.datacenters.put(dc.getName(), dc); // default DC
		this.applicationFileName = filename;
	}

	public void submitDeployApplication(Collection<SDNDatacenter> dcs, String filename) {
		for(SDNDatacenter dc: dcs) {
			if(dc != null)
				SDNBroker.datacenters.put(dc.getName(), dc); // default DC
		}
		this.applicationFileName = filename;
	}

	public void submitRequests(String filename) {
		this.workloadFileNames.add(filename);
	}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();

		switch(tag){
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreate(ev);
				break;
			case CloudSimTagsSDN.APPLICATION_SUBMIT:
				processApplication(ev.getSource(),(String) ev.getData());
				break;
			case CloudSimTagsSDN.APPLICATION_SUBMIT_ACK:
				applicationSubmitCompleted(ev);
				break;
			case CloudSimTagsSDN.REQUEST_COMPLETED:
				requestCompleted(ev);
				break;
			case CloudSimTagsSDN.REQUEST_FAILED:
				requestFailed(ev);
				break;
			case CloudSimTagsSDN.REQUEST_OFFER_MORE:
//				requestOfferMode(ev);
				break;
			default:
				System.out.println("Unknown event received by "+super.getName()+". Tag:"+ev.getTag());
				break;
		}
	}
	private void processVmCreate(SimEvent ev) {

	}

	private void requestFailed(SimEvent ev) {
		Request req = (Request) ev.getData();
		Workload wl = requestMap.remove(req.getRequestId());
		wl.failed = true;
		wl.writeResult();
	}

	private void requestCompleted(SimEvent ev) {
		Request req = (Request) ev.getData();
		Workload wl = requestMap.remove(req.getRequestId());
		wl.switchTime = req.switchTime;
		//step2:容器接收数据。这里实现为打印消息的时延结果。
		wl.writeResult();
	}

	private void applicationSubmitCompleted(SimEvent ev) {
		for(String filename: this.workloadFileNames) {
			WorkloadParser wParser = startWorkloadParser(filename);
			workloadId.put(wParser, SDNBroker.lastAppId);
			SDNBroker.lastAppId++; // 第几个文件对应的 workload
			scheduleRequest(wParser); //TODO: 创建消息
		}
	}

	/**
	 * 建立虚拟拓扑
	 */
	private void processApplication(int userId, String vmsFileName){
		SDNDatacenter defaultDC = SDNBroker.datacenters.entrySet().iterator().next().getValue();
		VirtualTopologyParser parser = new VirtualTopologyParser(defaultDC.getName(), vmsFileName, userId); //TODO: vm 到 host 的硬绑定
		// TODO:对于每个 dc，新建 vm(s)。由对应的 nos管理。vm即cn
		for(String dcName: SDNBroker.datacenters.keySet()) {
			SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
			NetworkOperatingSystem nos = dc.getNOS();
			for(SDNVm vm:parser.getVmList(dcName)) {
				nos.addVm(vm);
				SDNBroker.vmIdToDc.put(vm.getId(), dc);
			}
		}
		// 根据流（flows）的配置让对应的网络操作系统nos初始化流量通道。
		for(FlowConfig arc:parser.getArcList()) {
			SDNDatacenter srcDc = SDNBroker.vmIdToDc.get(arc.getSrcId());
			SDNDatacenter dstDc = SDNBroker.vmIdToDc.get(arc.getDstId());
			// 同一个平台内部
			if(srcDc.equals(dstDc)) {
				// Intra-DC traffic: create a virtual flow inside the DC
				srcDc.getNOS().addFlow(arc);
			} // 跨平台
			else {
				// Inter-DC traffic: Create it in inter-DC N.O.S.
				srcDc.getNOS().addFlow(arc);
				dstDc.getNOS().addFlow(arc);
			}
		}

		for(String dcName: SDNBroker.datacenters.keySet()) {
			SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
			NetworkOperatingSystem nos = dc.getNOS();
			nos.startDeployApplicatoin();
		}

		send(userId, 0, CloudSimTagsSDN.APPLICATION_SUBMIT_ACK, vmsFileName);
	}

	public static SDNDatacenter getDataCenterByName(String dcName) {
		return SDNBroker.datacenters.get(dcName);
	}

	public static SDNDatacenter getDataCenterByVmID(int vmId) {
		return SDNBroker.vmIdToDc.get(vmId);
	}

//	private void requestOfferMode(SimEvent ev) {
//		WorkloadParser wp = (WorkloadParser) ev.getData();
//		scheduleRequest(wp);
//	}

	/**
	 * parser初始化时模拟时间为-1，结束时间为inf
	 */
	private WorkloadParser startWorkloadParser(String workloadFile) {
		WorkloadParser workParser = new WorkloadParser(workloadFile, this.getId(), new UtilizationModelFull(),
				NetworkOperatingSystem.getVmNameToIdMap(), NetworkOperatingSystem.getFlowNameToIdMap());

		//System.err.println("SDNBroker.startWorkloadParser : DEBUGGGGGGGGGGG REMOVE here!");
		workParser.forceStartTime(experimentStartTime);
		workParser.forceFinishTime(experimentFinishTime);
		return workParser;

	}

	/**
	 * scheduleRequest从容器模块读取结果，创建消息
	 */
	private void scheduleRequest(WorkloadParser workParser) {
		int workloadId = this.workloadId.get(workParser);
		workParser.parseNextWorkloads(); /* 创建消息的核心函数 */
		List<Workload> parsedWorkloads = workParser.getParsedWorkloads();

		//将消息发送到仿真全局队列
		// --------- cloudsim自带的格式，可忽略 -----------------
		if(parsedWorkloads.size() > 0) {
			// Schedule the parsed workloads
			for(Workload wl: parsedWorkloads) {
				double scehduleTime = wl.time - CloudSim.clock();
				if(scehduleTime <0) {
					double timenow = CloudSim.clock();
					Log.printLine("**"+CloudSim.clock()+": SDNBroker.scheduleRequest(): abnormal start time." + wl);
					continue;
				}
				wl.appId = workloadId;
				SDNDatacenter dc = SDNBroker.vmIdToDc.get(wl.submitVmId);
				send(dc.getId(), scehduleTime, CloudSimTagsSDN.REQUEST_SUBMIT, wl.request);
				requestMap.put(wl.request.getTerminalRequest().getRequestId(), wl);
			}
		}
		// -----------------------------------------------------
	}

	public List<Workload> getWorkloads() {
//		return workloads;
		return null;
	}
}
