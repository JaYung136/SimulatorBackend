/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.core.SimEvent;

import java.util.Iterator;
import java.util.Set;

public class CloudSimEx extends CloudSim {
	private static long startTime;

	private static void setStartTimeMillis(long startedTime) {
		startTime=startedTime;
	}
	public static void setStartTime() {
		setStartTimeMillis(System.currentTimeMillis());
	}

	public static long getElapsedTimeSec() {
		long currentTime = System.currentTimeMillis();
		long elapsedTime = currentTime - startTime;
		elapsedTime /= 1000;

		return elapsedTime;
	}
	public static String getElapsedTimeString() {
		String ret ="";
		long elapsedTime = getElapsedTimeSec();
		ret = ""+elapsedTime/3600+":"+ (elapsedTime/60)%60+ ":"+elapsedTime%60;

		return ret;
	}

	public static int getNumFutureEvents() {
		return future.size() + deferred.size();
	}

	public static boolean hasMoreEvent(Set<Integer> excludeEventTag) {
		if(future.size() > 0) {
			Iterator<SimEvent> fit = future.iterator();
			while(fit.hasNext()) {
				SimEvent ev = fit.next();
				if(excludeEventTag.contains(ev.getTag()) != true)
					return true;
			}
		}
		if(deferred.size() > 0) {
			Iterator<SimEvent> fit = deferred.iterator();
			while(fit.hasNext()) {
				SimEvent ev = fit.next();
				if(excludeEventTag.contains(ev.getTag()) != true)
					return true;
			}
		}
		return false;
	}

	public static double getNextEventTime() {
		if(future.size() > 0) {
			Iterator<SimEvent> fit = future.iterator();
			SimEvent first = fit.next();
			if(first != null)
				return first.eventTime();
		}
		return -1;
	}
}
