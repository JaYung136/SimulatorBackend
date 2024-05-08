/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.sim.cloudsimsdn.sdn.parsers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.sim.cloudsimsdn.Cloudlet;
import org.sim.cloudsimsdn.Datacenter;
import org.sim.cloudsimsdn.UtilizationModel;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.Configuration;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNDatacenter;
import org.sim.cloudsimsdn.sdn.workload.*;
import org.sim.controller.AssignInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.sim.cloudsimsdn.core.CloudSim.assignInfoMap;
import static org.sim.cloudsimsdn.core.CloudSim.getEntityId;
import static org.sim.controller.SDNController.*;
import static org.sim.service.Constants.workloads;

public class WorkloadParser {
	private static final int NUM_PARSE_EACHTIME = 200;

	private double forcedStartTime = -1;
	private double forcedFinishTime = Double.POSITIVE_INFINITY;

	private final Map<String, Integer> vmNames;
	private final Map<String, Integer> flowNames;
	private String file;
	private int userId;
	private UtilizationModel utilizationModel;

	private List<Workload> parsedWorkloads;

	private WorkloadResultWriter resultWriter = null;

	private int workloadNum = 0;

	private BufferedReader bufReader = null;

	public WorkloadParser(String file, int userId, UtilizationModel cloudletUtilModel,
			Map<String, Integer> vmNameIdMap, Map<String, Integer> flowNameIdMap) {
		this.file = file;
		this.userId = userId;
		this.utilizationModel = cloudletUtilModel;
		this.vmNames = vmNameIdMap;
		this.flowNames = flowNameIdMap;

		String result_file = getResultFileName(this.file);
		resultWriter = new WorkloadResultWriter(result_file);
//		openFile();
	}

	public void forceStartTime(double forcedStartTime) {
		this.forcedStartTime = forcedStartTime;
	}

	public void forceFinishTime(double forcedFinishTime) {
		this.forcedFinishTime = forcedFinishTime;
	}

	public static String getResultFileName(String fileName) {
		String result_file = workload_result;
		return result_file;
	}

	/**
	 * 将文件的每一行(workload)依次添加到 parsedWorkloads中
	 */
	public void parseNextWorkloads() {
		this.parsedWorkloads = new ArrayList<Workload>();
		parseNext(NUM_PARSE_EACHTIME);
	}

	public List<Workload> getParsedWorkloads() {
		return this.parsedWorkloads;
	}


	public WorkloadResultWriter getResultWriter() {
		return resultWriter;
	}


	private int getVmId(String vmName) {
		Integer vmId = this.vmNames.get(vmName);
		if(vmId == null) {
			System.err.println("Cannot find VM name:"+vmName);
			return -1;
		}
		return vmId;
	}

	private Cloudlet generateCloudlet(long cloudletId, int vmId, int length) {
		int peNum=1;
		long fileSize = 300;
		long outputSize = 300;
		Cloudlet cloudlet= new Cloudlet((int)cloudletId, length, peNum, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		cloudlet.setUserId(userId);
		cloudlet.setVmId(vmId);

		return cloudlet;
	}

	// Cloud_Len -> /FlowId/ -> ToVmId -> PktSize
//	private Request parseRequest(int fromVmId, Queue<String> lineitems) {
//		long cloudletLen = Long.parseLong(lineitems.poll());
//		cloudletLen*=Configuration.CPU_SIZE_MULTIPLY;
//
//		Request req = new Request(userId);
//		Cloudlet cl = generateCloudlet(req.getRequestId(), fromVmId, (int) cloudletLen);
//		//this.parsedCloudlets.add(cl);
//
//		Processing proc = new Processing(cl);
//		req.addActivity(proc);
//
//		if(lineitems.size() != 0) {
//			// Has more requests after this. Create a transmission and add
//			String linkName = lineitems.poll();
//			Integer flowId = this.flowNames.get(linkName);
//
//			if(flowId == null) {
//				throw new IllegalArgumentException("No such link name in virtual.json:"+linkName);
//			}
//
//			String vmName = lineitems.poll();
//			destVMName = vmName;
//			int toVmId = getVmId(vmName);
//			SDNDatacenter toDC = (SDNDatacenter) SDNDatacenter.findDatacenterGlobal(toVmId);
//
//			long pktSize = Long.parseLong(lineitems.poll());
//			pktSize*=Configuration.NETWORK_PACKET_SIZE_MULTIPLY;
//			if(pktSize<0)
//				pktSize=0;
//
//			Request nextReq = parseRequest(toVmId, lineitems);
//
//			Transmission trans = new Transmission(fromVmId, toVmId, pktSize, flowId, nextReq);
//			req.addActivity(trans);
//		} else {
//			// this is the last request.
//		}
//		return req;
//	}

	private void openFile() {
		try {
			bufReader = new BufferedReader(new FileReader(Configuration.workingDirectory+file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		try {
			@SuppressWarnings("unused")
			String head=bufReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static double random_factor = 0.0;
	private List<Double> calMsgStarttimes(Double starttime, Double pausestart, Double pauseend, Double endtime, Double containerperiod, Double msgperiod, Double simulationend, Boolean inplatfrom) {
		ArrayList<Double> stimes = new ArrayList<>();
		if(random_factor >= msgperiod)
			random_factor = 0.0;
//		if(inplatfrom)
//			starttime += msgperiod*Math.random(); //TODO: 消息起始时间随机化 msgtime+msgperiod*Math.random();
		// 迭代多个容器生命周期
		while(endtime < simulationend*0.000001){
			/**
			 * 容器的一个生命周期时间内，计算所有的消息发送时间
			 * |(starttime)--------(starttime+pausestart)|  暂停  |(starttime+pausestart+pauseend)--------(endtime)|
			 * while starttime <= msgtime < starttime+pausestart:
			 * 		msgtime += msgperiod;
			 * while starttime+pausestart+pauseend <= msgtime < endtime:
			 * 		msgtime += msgperiod;
			 */
			Double msgtime = starttime;
			while(starttime <= msgtime && msgtime < starttime+pausestart){
				stimes.add(msgtime);
				msgtime += msgperiod;
			}
			msgtime = starttime+pausestart+pauseend;
			while(starttime+pausestart+pauseend <= msgtime && msgtime < endtime){
				stimes.add(msgtime);
				msgtime += msgperiod;
			}
			starttime += containerperiod;
			endtime += containerperiod;
		}
		random_factor += 1.2;
		return stimes;
	}
	private void parseNext(int numRequests) {
//		String line;
//		System.out.println("########################");
//		try{
////			JSONArray pure_msgs = new JSONArray();
//			String xml = Files.readString(Path.of(input_app));
//			JSONArray apps = XML.toJSONObject(xml).getJSONObject("AppInfo").getJSONArray("application");
//			/**
//			 * 制作纯净消息，仅包含必要字段：
//			 * Name 消息名
//			 * SrcIP 发送方容器ip
//			 * DstIP 接收方容器ip
//			 * DstName 接收方任务名称
//			 * AppPeriod 任务周期 单位秒
//			 * MsgPeriod 消息周期 单位秒
//			 * MessageSize 消息大小
//			 */
//			int jobid = 1;
//			for(Object obj : apps) {
//				JSONObject app = (JSONObject) obj;
//				String src = app.getString("IpAddress");
//				Double appPeriod = app.getDouble("Period")*contractRate;
//				JSONObject tem = app.getJSONObject("A653SamplingPort");
//				try{
//					tem = tem.getJSONObject("A664Message");
//				}catch (Exception e){
//					continue;
//				}
//				Object dataField = tem.opt("A653SamplingPort");
//				//case1:向>1个cn发送数据包
//				if (dataField instanceof JSONArray) {
//					JSONArray msgs = (JSONArray) dataField;
//					for(Object objj: msgs){
//						JSONObject msg = (JSONObject) objj;
//						JSONObject puremsg = new JSONObject();
//						puremsg.put("Name", msg.getString("Name"))
//								.put("SrcIP",src)
//								.put("DstIP",msg.getString("IpAddress"))
//								.put("DstName",msg.getString("AppName"))
//								.put("AppPeriod",appPeriod)
//								.put("MsgPeriod", (msg.getDouble("SamplePeriod"))*contractRate)
//								.put("MessageSize",msg.getInt("MessageSize")*0.008)//单位b
//						;
//						pure_msgs.put(puremsg);
//						/** TODO: 为每条message创建workload示例
//						 *根据容器起始、暂停开始、暂停结束、容器结束、容器周期间隔、消息周期间隔、仿真截止时间
//						 * 得到若干的消息起始时间
//						 */
//						AssignInfo ai = assignInfoMap.get(src);
//						double MsgPeriod = msg.getDouble("SamplePeriod") * contractRate;
//						//TODO: 无线取消随机化
//						AssignInfo dstai = assignInfoMap.get(msg.getString("IpAddress"));
//						List<Double> msgStarttimes = calMsgStarttimes(ai.starttime, ai.pausestart, ai.pauseend, ai.endtime,
//								ai.containerperiod, MsgPeriod, simulationStopTime, ai.equals(dstai));
//						for(Double msgstart : msgStarttimes){
//							Workload wl = new Workload(workloadNum++, jobid, this.resultWriter);
//							wl.msgName = msg.getString("Name");
//							wl.time = msgstart;
//							wl.submitVmName = src;
//							wl.submitVmId = getVmId(src);
//							wl.destVmName = msg.getString("IpAddress");
//							wl.destVmId = getVmId(wl.destVmName);
//							wl.submitPktSize = msg.getInt("MessageSize")*0.008;
//							Request req = new Request(userId);
//							req.addActivity(
//									new Processing(
//											generateCloudlet(req.getRequestId(), wl.submitVmId, 0)
//									)
//							);
//							Request endreq = new Request(userId);
//							endreq.addActivity(
//									new Processing(
//											generateCloudlet(req.getRequestId(), wl.destVmId, 0)
//									)
//							);
//							req.addActivity(new Transmission(wl.submitVmId, wl.destVmId, wl.submitPktSize, this.flowNames.get("default"), endreq));
//							wl.request = req;
//							parsedWorkloads.add(wl);
//							++jobid;
//						}
//						/****************************/
//					}
//				}
//				//case2:仅向1个cn发送数据包
//				else {
//					JSONObject msg = (JSONObject) dataField;
//					JSONObject puremsg = new JSONObject();
//					puremsg.put("Name", msg.getString("Name"))
//							.put("SrcIP",src)
//							.put("DstIP",msg.getString("IpAddress"))
//							.put("DstName",msg.getString("AppName"))
//							.put("AppPeriod",appPeriod)
//							.put("MsgPeriod", msg.getDouble("SamplePeriod")*contractRate)
//							.put("MessageSize",msg.getInt("MessageSize")*0.008)//单位Kb?
//					;
//					pure_msgs.put(puremsg);
//					/** TODO: 为每条message创建workload实例
//					 *根据容器起始、暂停开始、暂停结束、容器结束、容器周期间隔、消息周期间隔、仿真截止时间
//					 * 得到若干的消息起始时间
//					 */
//					AssignInfo ai = assignInfoMap.get(src);
//					double MsgPeriod = msg.getDouble("SamplePeriod") * contractRate;
//					//TODO: 无线取消随机化
//					AssignInfo dstai = assignInfoMap.get(msg.getString("IpAddress"));
//					List<Double> msgStarttimes = calMsgStarttimes(ai.starttime, ai.pausestart, ai.pauseend, ai.endtime,
//							ai.containerperiod, MsgPeriod, simulationStopTime, ai.equals(dstai));
//					for(Double msgstart : msgStarttimes){
//						Workload wl = new Workload(workloadNum++, jobid, this.resultWriter);
//						wl.msgName = msg.getString("Name");
//						wl.time = msgstart;
//						wl.submitVmName = src;
//						wl.submitVmId = getVmId(src);
//						wl.destVmName = msg.getString("IpAddress");
//						wl.destVmId = getVmId(wl.destVmName);
//						wl.submitPktSize = msg.getInt("MessageSize")*0.008;
//						Request req = new Request(userId);
//						req.addActivity(
//								new Processing(
//										generateCloudlet(req.getRequestId(), wl.submitVmId, 0)
//								)
//						);
//						Request endreq = new Request(userId);
//						endreq.addActivity(
//								new Processing(
//										generateCloudlet(req.getRequestId(), wl.destVmId, 0)
//								)
//						);
//						req.addActivity(new Transmission(wl.submitVmId, wl.destVmId, wl.submitPktSize, this.flowNames.get("default"), endreq));
//						parsedWorkloads.add(wl);
//						wl.request = req;
//						++jobid;
//					}
//					/****************************/
//				}
//			}
//
//			String jsonPrettyPrintString = pure_msgs.toString(4);
//			//保存格式化后的json
//			FileWriter writer = new FileWriter(workloadf);
//			writer.write(jsonPrettyPrintString);
//			writer.close();
//		}catch (Exception e){
//			e.printStackTrace();
//		}
		try{
			for(Workload wl:workloads){
				wl.resultWriter = this.resultWriter;
				wl.time = wl.time * contractRate;
				wl.submitVmId = getVmId(wl.submitVmName);
				wl.destVmId = getVmId(wl.destVmName);
				Request req = new Request(userId);
				req.addActivity(
						new Processing(
								generateCloudlet(req.getRequestId(), wl.submitVmId, 0)
						)
				);
				Request endreq = new Request(userId);
				endreq.addActivity(
						new Processing(
								generateCloudlet(req.getRequestId(), wl.destVmId, 0)
						)
				);
				req.addActivity(new Transmission(wl.submitVmId, wl.destVmId, wl.submitPktSize, this.flowNames.get("default"), endreq));
				wl.request = req;
				parsedWorkloads.add(wl);
			}

		}catch (Exception e){
			e.printStackTrace();
		}

		return;

	}

	public int getWorkloadNum() {
		return workloadNum;
	}

	public int getGroupId() {
		String first_word = this.file.split("_")[0];
		int groupId = 0;
		try {
			groupId = Integer.parseInt(first_word);
		} catch (NumberFormatException e) {
			// Do nothing
		}
		return groupId;
	}
}
