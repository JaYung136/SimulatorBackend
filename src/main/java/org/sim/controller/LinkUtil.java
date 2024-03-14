package org.sim.controller;

import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;
import org.sim.cloudsimsdn.sdn.virtualcomponents.Channel;

import java.awt.event.HierarchyBoundsAdapter;
import java.util.ArrayList;
import java.util.List;

public class LinkUtil {
    public boolean printable = false;
    public double timeUnit = 0.0;
    public String linkname;
    public String lowOrder;
    public String highOrder;
    public double totalBW;
    public List<Double> recordTimes = new ArrayList<>();
    public List<Double> UnitUtilForward = new ArrayList<>();
    public List<Double> UnitUtilBackward = new ArrayList<>();

    public LinkUtil(double timeUnit, String linkname, String lowOrder, String highOrder, double totalBW) {
        this.timeUnit = timeUnit;
        this.linkname = linkname;
        this.lowOrder = lowOrder;
        this.highOrder = highOrder;
        this.totalBW = totalBW;
        this.printable = false;
    }

    public LinkUtil(){
    }
}
