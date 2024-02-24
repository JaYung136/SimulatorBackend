package org.sim.service;


import org.apache.commons.math3.util.Pair;
import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.power.models.PowerModel;
import org.sim.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G4Xeon3040;
import org.sim.cloudbus.cloudsim.power.models.PowerModelSpecPowerHpProLiantMl110G5Xeon3075;
import org.sim.controller.Result;
import org.sim.service.result.FaultRecord;
import org.sim.service.result.LogEntity;
import org.sim.workflowsim.CondorVM;
import org.sim.workflowsim.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {
    public static Double finishTime = 0.0;
    public static Double averageMIPS = 0.0;
    public static Boolean ifSimulate = true;
    public static Integer repeatTime = 1;
    public static Integer migrateNum = 1;
    public static List<Task> taskList = new ArrayList<>();
    public static Map<String, Integer> schedulerResult = new HashMap<>();
    public static Map<String, String> staticApp2Host = new HashMap<>();
    public static File faultFile = null;
    public static File containerFile = null;
    public static File appFile = null;
    public static File hostFile = null;
    public static File topoFile = null;
    public static Map<String, ContainerInfo> containerInfoMap = new HashMap<>();
    public static Double cpuUp = 1.1;
    public static Double ramUp = 1.1;
    public static List<? extends Cloudlet> resultPods = new ArrayList<>();
    public static List<? extends Host> hosts = new ArrayList<>();
    public static int Scheduler_Id = -1;
    public static Map<String, Boolean> app2Con = new HashMap<>();
    public static Map<Integer, String> id2Name = new HashMap<>();
    public static Map<String, Container> name2Container = new HashMap<>();
    public static Map<Integer, Pair<Double, Double>> pause = new HashMap<>();
    public static boolean nodeEnough = true;
    public static List<List<String>> apps = new ArrayList<>();
    public static Map<String, String> ip2taskName = new HashMap<>();
    public static Map<String, List<Pair<String, String>>> name2Ips = new HashMap<>();
    public static String LOG_PATH = "";
    public static String FAULT_LOG_PATH = "";
    public static List<LogEntity> logs = new ArrayList<>();
    public static List<Result> results = new ArrayList<>();
    public static Map<String, Integer> faultNum = new HashMap<>();
    public static List<FaultRecord> records = new ArrayList<>();


    public final static PowerModel[] HOST_POWER = {
            new PowerModelSpecPowerHpProLiantMl110G4Xeon3040(),
            new PowerModelSpecPowerHpProLiantMl110G5Xeon3075()
    };

}
