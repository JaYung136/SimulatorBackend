package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;

public interface HostFactory {
	public abstract SDNHost createHost(int ram, long bw, long storage, long pes, double mips, String name);
}
