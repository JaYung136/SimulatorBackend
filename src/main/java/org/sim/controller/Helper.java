package org.sim.controller;

import org.sim.cloudbus.cloudsim.Log;
import org.sim.service.Constants;
import org.sim.service.service;

import java.util.ArrayList;
import java.util.HashMap;

public class Helper {
    private service service;

    private void resetForSimulator() {
        Constants.lastTime = 0.0;
        Constants.results = new ArrayList<>();
        Constants.logs = new ArrayList<>();
        Constants.resultPods = new ArrayList<>();
        Constants.id2Name = new HashMap<>();
        Constants.taskList = new ArrayList<>();
        Constants.faultNum = new HashMap<>();
        Constants.records = new ArrayList<>();
        Constants.ip2taskName = new HashMap<>();
        Constants.name2Ips = new HashMap<>();
        Constants.finishTime = 0.0;
        service = new service();
    }
    public Message simulate(Integer a, Integer repeatTime, Double lastTime) {
        resetForSimulator();
        Constants.repeatTime  = Integer.MAX_VALUE;
        Constants.ifSimulate = true;
        Constants.lastTime = lastTime;
        try {
            service.simulate(a);
            if(!Constants.nodeEnough) {
                Log.printLine("节点资源不足");
                return Message.Fail("集群中节点资源不足");
            }
            return Message.Success(null);
        } catch (Exception e) {
            return Message.Fail(e.getMessage());
        }
    }

    public Message simForCpu(Double lastTime) {
        return simulate(8, 3, lastTime);
    }
}
