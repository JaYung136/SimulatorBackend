/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.physicalcomponents.switches;

/**
 * Represent core switch
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class CoreSwitch extends Switch {

	public CoreSwitch(String name,long bw, long iops, int upports, int downports) {
		super(name, bw, iops, upports, downports);
		//if (upports>0) throw new IllegalArgumentException("Core switches cannot have uplinks.");
	}

}
