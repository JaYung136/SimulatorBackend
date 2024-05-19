/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.parsers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.sim.cloudsimsdn.sdn.HostFactory;
import org.sim.cloudsimsdn.sdn.HostFactorySimple;
import org.sim.cloudsimsdn.sdn.nos.NetworkOperatingSystem;
import org.sim.cloudsimsdn.sdn.nos.NetworkOperatingSystemSimple;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Link;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * This class parses Physical Topology JSON file.
 * It supports multiple data centers.
 *
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopologyParser {
	private String filename;

	private Multimap<String, SDNHost> sdnHosts;
	private Multimap<String, Switch> switches;
	private List<Link> links = new ArrayList<Link>();
	private Hashtable<String, Node> nameNodeTable = new Hashtable<String, Node>();
	private HostFactory hostFactory = null;
	public Map<String, Node> dcAndWirelessGateway = new HashMap<>();
	public PhysicalTopologyParser(String jsonFilename, HostFactory hostFactory) {
		sdnHosts = HashMultimap.create();
		switches = HashMultimap.create();
		this.hostFactory = hostFactory;

		this.filename = jsonFilename;
	}

	/**
	 * 创建物理组件 DataCenter、NOS、Node、Link等
	 */
	public static Map<String, NetworkOperatingSystem> loadPhysicalTopologyMultiDC(String physicalTopologyFilename) {
		PhysicalTopologyParser parser = new PhysicalTopologyParser(physicalTopologyFilename, new HostFactorySimple());
		Map<String, String> dcNameType = parser.parseDatacenters(); // DC Name -> DC Type
		Map<String, NetworkOperatingSystem> netOsList = new HashMap<String, NetworkOperatingSystem>();

		for(String dcName: dcNameType.keySet()) {
			NetworkOperatingSystem nos;
			nos = new NetworkOperatingSystemSimple("NOS_"+dcName);
			netOsList.put(dcName, nos);
			//TODO: 在这里解析中间文件，创建 switch 和 host
			parser.parseNode(dcName);
		}
		//TODO: 在这里解析中间文件，创建 link
		parser.parseLink();

		// --------- cloudsim自带的格式，可忽略 -----------------
		for(String dcName: dcNameType.keySet()) {
			if(!"network".equals(dcNameType.get(dcName))) {
				NetworkOperatingSystem nos = netOsList.get(dcName);
				nos.configurePhysicalTopology(parser.getHosts(dcName), parser.getSwitches(dcName), parser.getLinks());
			}
		}
		for(String dcName: dcNameType.keySet()) {
			if("network".equals(dcNameType.get(dcName))) {
				NetworkOperatingSystem nos = netOsList.get(dcName);
				nos.configurePhysicalTopology(parser.getHosts(dcName), parser.getSwitches(dcName), parser.getLinks());
			}
		}
		// -----------------------------------------------------

		return netOsList;
	}

	public static Map<String, Node> getDcAndWirelessGateway(String physicalTopologyFilename) {
		PhysicalTopologyParser parser = new PhysicalTopologyParser(physicalTopologyFilename, new HostFactorySimple());
		Map<String, String> dcNameType = parser.parseDatacenters();
		for(String dcName: dcNameType.keySet()) {
			parser.parseWirelessGateway(dcName);
		}
		return parser.dcAndWirelessGateway;
	}

//	public static void loadPhysicalTopologySingleDC(String physicalTopologyFilename, NetworkOperatingSystem nos, HostFactory hostFactory) {
//		PhysicalTopologyParser parser = new PhysicalTopologyParser(physicalTopologyFilename, hostFactory);
//		parser.parse(nos);
//		nos.configurePhysicalTopology(parser.getHosts(), parser.getSwitches(), parser.getLinks());
//	}

	public Collection<SDNHost> getHosts() {
		return this.sdnHosts.values();
	}

	public Collection<SDNHost> getHosts(String dcName) {
		return this.sdnHosts.get(dcName);
	}

	public Collection<Switch> getSwitches() {
		return this.switches.values();
	}

	public Collection<Switch> getSwitches(String dcName) {
		return this.switches.get(dcName);
	}

	public List<Link> getLinks() {
		return this.links;
	}

	public Map<String, String> parseDatacenters() {
		HashMap<String, String> dcNameType = new HashMap<String, String>();
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));

    		JSONArray datacenters = (JSONArray) doc.get("datacenters");
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter = datacenters.iterator();
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String dcName = (String) node.get("name");
				String type = (String) node.get("type");

				dcNameType.put(dcName, type);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		return dcNameType;
	}

	/**
	 * parse switch 和 host
	 * 并在该函数中创建 dc 与 wirelessGateway 之间的映射
	 */
	public void parseNode(String datacenterName) {
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));

    		// Get Nodes (Switches and Hosts)
    		JSONArray nodes = (JSONArray) doc.get("nodes");
    		@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter =nodes.iterator();
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String nodeType = (String) node.get("type");
				String nodeName = (String) node.get("name");
				String dcName = (String) node.get("datacenter");
				if(datacenterName != null && !datacenterName.equals(dcName)) {
					continue;
				}

				if(nodeType.equalsIgnoreCase("host")){
					////////////////////////////////////////
					// Host
					////////////////////////////////////////
					/**
					 * 忽略这些参数
					 * host主要在容器模块的仿真中被使用，由容器模块创建
					 * 网络仿真中host无作用
					 */
					long pes = (Long) node.get("pes");
					long mips = (Long) node.get("mips");
					int ram = new BigDecimal((Long)node.get("ram")).intValueExact();
					long storage = (Long) node.get("storage");
					long bw = new BigDecimal((Long)node.get("bw")).longValueExact();
					int num = 1;
					if (node.get("nums")!= null)
						num = new BigDecimal((Long)node.get("nums")).intValueExact();
					for(int n = 0; n< num; n++) {
						String nodeName2 = nodeName;
						if(num >1) nodeName2 = nodeName + n;
						SDNHost sdnHost = hostFactory.createHost(ram, bw, storage, pes, mips, nodeName);
						nameNodeTable.put(nodeName2, sdnHost);
						//hostId++;
						this.sdnHosts.put(dcName, sdnHost);
					}

				} else {
					////////////////////////////////////////
					// Switch
					////////////////////////////////////////
					int MAX_PORTS = 256;
					long bw = new BigDecimal((Long)node.get("bw")).longValueExact();
					long iops = 0;//(Long) node.get("iops");
					int upports = MAX_PORTS;
					int downports = MAX_PORTS;
					Switch sw = null;
					//核心交换机，连接交换机与交换机
					if(nodeType.equalsIgnoreCase("core")) {
						sw = new CoreSwitch(nodeName, bw, iops, upports, downports);
					//aggregate不使用
					} else if (nodeType.equalsIgnoreCase("aggregate")){
						sw = new AggregationSwitch(nodeName, bw, iops, upports, downports);
					//边缘交换机，连接主机与主机 or 主机与交换机
					} else if (nodeType.equalsIgnoreCase("edge")){
						sw = new EdgeSwitch(nodeName, bw, iops, upports, downports);
					//intercloud代表无线网络
					} else if (nodeType.equalsIgnoreCase("intercloud")){
						sw = new IntercloudSwitch(nodeName, bw, iops, upports, downports);
						this.dcAndWirelessGateway.put(datacenterName, sw);
					//平台的无线接入点
					} else if (nodeType.equalsIgnoreCase("gateway")){
						if(nameNodeTable.get(nodeName) != null)
							sw = (Switch)nameNodeTable.get(nodeName);
						else
							sw = new GatewaySwitch(nodeName, bw, iops, upports, downports);
					} else {
						throw new IllegalArgumentException("No switch found!");
					}
					if(sw != null) {
						nameNodeTable.put(nodeName, sw);
						this.switches.put(dcName, sw);
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 创建 dc 与 wirelessGateway 之间的映射
	 */
	public void parseWirelessGateway(String datacenterName) {
		try {
			JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
			// Get Nodes (Switches and Hosts)
			JSONArray nodes = (JSONArray) doc.get("nodes");
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> iter =nodes.iterator();
			while(iter.hasNext()){
				JSONObject node = iter.next();
				String nodeType = (String) node.get("type");
				String nodeName = (String) node.get("name");
				String dcName = (String) node.get("datacenter");
				if(datacenterName != null && !datacenterName.equals(dcName)) {
					continue;
				}
				if(nodeType.equalsIgnoreCase("host")){
				} else {
					////////////////////////////////////////
					// Switch
					////////////////////////////////////////
					int MAX_PORTS = 256;
					long bw = new BigDecimal((Long)node.get("bw")).longValueExact();
					long iops = (Long) node.get("iops");
					int upports = MAX_PORTS;
					int downports = MAX_PORTS;
					Switch sw = null;
					if (nodeType.equalsIgnoreCase("gateway")){
						sw = new IntercloudSwitch(nodeName, bw, iops, upports, downports);
						this.dcAndWirelessGateway.put(datacenterName, sw);
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void parseLink() {
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(this.filename));
			JSONArray links = (JSONArray) doc.get("links");
			@SuppressWarnings("unchecked")
			Iterator<JSONObject> linksIter =links.iterator();
			while(linksIter.hasNext()){
				JSONObject link = linksIter.next();
				String src = (String) link.get("source");//链路连接的一端节点
				String dst = (String) link.get("destination");//链路连接的另一端节点
				double lat = Double.parseDouble((String)link.get("latency"));//不使用
				String name = (String) link.get("name");//链路名称
				Node srcNode = nameNodeTable.get(src);
				Node dstNode = nameNodeTable.get(dst);
				//TODO: bw在addLink函数中根据switch赋值
				Link l = new Link(srcNode, dstNode, lat, -1, name); //(Double) link.get("bw")); // Temporary Link (blueprint) to create the real one in NOS
				this.links.add(l);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	public Hashtable<String, Node> getNameNode() {
		return nameNodeTable;
	}
}
