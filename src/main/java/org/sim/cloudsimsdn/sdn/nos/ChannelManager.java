package org.sim.cloudsimsdn.sdn.nos;

import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Link;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.GatewaySwitch;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.IntercloudSwitch;
import org.sim.cloudsimsdn.sdn.virtualcomponents.Channel;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;
import org.sim.cloudsimsdn.sdn.virtualcomponents.VirtualNetworkMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ChannelManager {
	protected NetworkOperatingSystem nos = null;
	protected VirtualNetworkMapper vnMapper = null;

	// Processing requests
	protected HashMap<String, Channel> channelTable = new HashMap<String, Channel>();	// getKey(fromVM, toVM, flowID) -> Channel
	protected List<Channel> tempRemovedChannels = new LinkedList<Channel>();

	public ChannelManager(NetworkOperatingSystem nos, VirtualNetworkMapper vnMapper) {
		this.nos = nos;
		this.vnMapper = vnMapper;
	}

	/**
	 * 从 srcHost 到 dstHost 的整个 links 链（linksAll）。
	 * 但是 nodes、links、bw 只包含到 gateway 部分
	 */
	public Channel createChannel(int src, int dst, int flowId, Node srcNode) {
		// 采用最短路径原则创建Channel。这是Cloudsim的自带函数，可忽略
		vnMapper.updateDynamicForwardingTableRec(srcNode, src, dst, flowId, false);

		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		Node origin = srcNode; //发送方节点(主机)
		Node dest = origin.getCnRoute(src, dst, flowId); //通往接收方主机的下一个节点(交换机)
		if(dest==null) {
			throw new IllegalArgumentException("createChannel(): dest is null, cannot create channel! ");
		}
		double lowestBw = Double.POSITIVE_INFINITY;
		double reqBw = 0;
		if(flowId != -1) {
			reqBw = nos.getBandwidthBackup(flowId);
			if(reqBw == 0)
				throw new RuntimeException("reqBW cannot be zero for dedicated channels!!"+flowId);
		}
		//channel中加入数据包的发送节点
		nodes.add(origin);
		boolean isEther = true;
		boolean isWireless = false;
		//进入循环，不断加入节点，直到channel完整
		while(true) {
			//当前节点到下一个节点，选择的链路
			Link link = origin.getLinkTo(dest);
			if(dest instanceof IntercloudSwitch){
				isWireless = true; //碰到无线网
			}
			if (origin instanceof GatewaySwitch) {
				isEther = false; //碰到无线接入点，会经过无线网
			}
			if(link == null)
				throw new IllegalArgumentException("Link is NULL for srcNode:"+origin+" -> dstNode:"+dest);
			//channel中加入选择的链路和下一个节点
			links.add(link);
			nodes.add(dest);
			//计算链路的空闲带宽，channel取所有组成链路中的最小值
			if(isEther && lowestBw > link.getFreeBandwidth(origin)) {
				lowestBw = link.getFreeBandwidth(origin);
			}
			// 到接收方主机了，链路完整，退出循环
			if(dest instanceof SDNHost)
				break;
			// 否则当前节点跳到下个节点，继续循环
			origin = dest;
			dest = origin.getCnRoute(src, dst, flowId);
		}

		Channel channel=new Channel(flowId, src, dst, nodes, links, reqBw,
				(SDNVm)NetworkOperatingSystem.findVmGlobal(src), (SDNVm)NetworkOperatingSystem.findVmGlobal(dst), isWireless, 0);

		return channel;
	}

	/**
	 * 在 channelTable 中新建一条 channel
	 * 为新的 channel 设置带宽
	 * 发送 SDB-8号消息通知其他所有 channels 更新带宽
	 */
	public void addChannel(int src, int dst, int chId, Channel ch) {
			this.channelTable.put(getChannelKey(src, dst, chId), ch);
			ch.initialize();
			ch.adjustSharedBandwidthAlongLink();
			// TODO:更新channel带宽
			this.nos.lastAdjustAllChannelTime = CloudSim.clock();
			this.nos.channelManager.adjustAllChannel();
	}

	public void addWirelessChannel(int src, int dst, int chId, Channel ch) {
		this.channelTable.put(getChannelKey(src, dst, chId), ch);
		ch.totalLatency = 0;
	}

	public Channel findChannel(int from, int to, int channelId) {
		Channel channel=channelTable.get(getChannelKey(from, to, channelId));
		if (channel == null) {
			channel=channelTable.get(getChannelKey(from,to));
		}
		return channel;
	}

	/**
	 * 遍历所有 channels，若有空闲 chan(没有传输的包)，删除它。
	 */
	private void updateChannel() {
		//TODO: 更新带宽利用率
		//step5:销毁通道，释放网络资源
		List<String> removeCh = new ArrayList<String>();
		for(String key:this.channelTable.keySet()) {
			Channel ch = this.channelTable.get(key);
			if(ch.getActiveTransmissionNum() == 0) {
				//普通端到端通道
				removeCh.add(key);
				//无线传输通道
				CloudSim.wirelessScheduler.RemoveChannel(ch);
			}
		}

		for(String key:removeCh) {
			removeChannel(key);
		}
	}

	public Channel removeChannel(int srcVm, int dstVm, int flowId) {
		if(findChannel(srcVm, dstVm, flowId) == null)
			return null;
		return removeChannel(getChannelKey(srcVm, dstVm, flowId));
	}

	private Channel removeChannel(String key) {
		//System.err.println("NOS.removeChannel:"+key);
		Channel ch = this.channelTable.remove(key);
		ch.terminate();
		// TODO:手动更新channel带宽
//			nos.sendAdjustAllChannelEvent();
		this.nos.lastAdjustAllChannelTime = CloudSim.clock();
		this.nos.channelManager.adjustAllChannel();

		tempRemovedChannels.add(ch);
		return ch;
	}

	private void resetTempRemovedChannel() {
		tempRemovedChannels = new LinkedList<Channel>();
	}

	public void adjustAllChannel() {
		//TODO: 更新带宽利用率
//		this.nos.updateBWMonitor(1);
		double timenow = CloudSim.clock();
		for(Channel ch:this.channelTable.values()) {
			if(ch.adjustSharedBandwidthAlongLink()) {
				// Channel BW is changed. send event.
			}
		}
	}

	public double nextFinishTime() {
		double earliestEft = Double.POSITIVE_INFINITY; // earliest event_finish_time
		//遍历所有的端到端虚拟链路，计算每一个channel完成传输数据包的时间，取最小值
		for(Channel ch:channelTable.values()){
			double timenow = CloudSim.clock();
			double eft = ch.nextFinishTime();
			if (eft<earliestEft){
				earliestEft=eft;
			}
		}
		return earliestEft;

	}

	/**
	 * 更新数据包的传输情况
	 * 找出所有完成传输的 channels，通知对应平台
	 * 之后删除空闲 channels。
	 */
	public boolean updatePacketProcessing() {
		boolean needSendEvent = false;

		LinkedList<Channel> completeChannels = new LinkedList<Channel>();
		// 遍历每条channel，更新数据包中已经被传输的长度
		for(Channel ch:channelTable.values()){
			double timenow = CloudSim.clock();
			//更新传输长度，如果传输完毕，返回ture
			boolean isCompleted = ch.updatePacketProcessing();

			if(isCompleted) {
				completeChannels.add(ch);
				if(ch.getActiveTransmissionNum() != 0)
				{
					needSendEvent = true;
				}

			} else {
				needSendEvent = true;
			}
		}

		if(completeChannels.size() != 0) {
			//step4:请见此函数
			nos.processCompletePackets(completeChannels); // 通知收包的平台，处理数据包
			//step5:请见此函数
			updateChannel(); // 删除空闲 channels
		}

		return needSendEvent;
	}

	public long getTotalNumPackets() {
		long numPackets=0;
		for(Channel ch:channelTable.values()) {
			numPackets += ch.getActiveTransmissionNum();
		}

		return numPackets;
	}

	public long getTotalChannelNum() {
		return channelTable.size();
	}

	public static String getChannelKey(int origin, int destination) {
		return origin+"-"+destination;
	}

	public static String getChannelKey(int origin, int destination, int appId) {
		return getChannelKey(origin,destination)+"-"+appId;
	}

//	public void updateMonitor(double monitoringTimeUnit) {
//		// Update bandwidth consumption of all channels
//		for(Channel ch:channelTable.values()) {
//			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
//		}
//
//		for(Channel ch:tempRemovedChannels) {
//			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
//		}
//		this.resetTempRemovedChannel();
//
//	}

}
