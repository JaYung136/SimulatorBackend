/**
 * Copyright 2019-2020 University Of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.com.wfc.cloudsim/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sim.workflowsim;




import org.apache.commons.math3.util.Pair;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.sim.cloudbus.cloudsim.*;
import org.sim.cloudbus.cloudsim.*;
import org.sim.service.Constants;
import org.sim.service.Container;
import org.sim.service.ContainerInfo;
import org.sim.workflowsim.utils.DistributionGenerator;
import org.sim.workflowsim.utils.Parameters;
import org.sim.workflowsim.utils.ReplicaCatalog;
import org.sim.cloudbus.cloudsim.*;
import org.sim.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.sim.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.sim.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * YamlUtil parse a DAX into tasks so that WorkflowSim can manage them
 *
 * @author Arman Riazi
 * @since WorkflowSim Toolkit 1.0
 * @date Aug 23, 2013
 * @date Nov 9, 2014
 */
public final class XmlUtil {

    /**
     * The path to DAX file.
     */
    private final String daxPath;
    /**
     * The path to DAX files.
     */
    private final List<String> daxPaths;
    /**
     * All tasks.
     */
    private List<Task> taskList;

    private List<Host> hostList;

    private List<CondorVM> containerList;

    private int hostId = 0;


    /**
     * User id. used to create a new task.
     */
    private final int userId;

    /**
     * current job id. In case multiple workflow submission
     */
    private int jobIdStartsFrom;

    public DistributionGenerator.DistributionFamily distributionFamily;

    public Double scale;
    public Double shape;
    public List<Host> getHostList() {
        return hostList;
    }

    public void setHostList(List<Host> hostList) {
        this.hostList = hostList;
    }

    public List<CondorVM> getContainers() {return this.containerList;}

    /**
     * Gets the task list
     *
     * @return the task list
     */
    @SuppressWarnings("unchecked")
    public List<Task> getTaskList() {
        return taskList;
    }

    /**
     * Sets the task list
     *
     * @param taskList the task list
     */
    protected void setTaskList(List<Task> taskList) {
        this.taskList = taskList;
    }
    /**
     * Map from task name to task.
     */
    protected Map<String, Task> mName2Task;

    /**
     * Initialize a WorkflowParser
     *
     * @param userId the user id. Currently we have just checked single user
     * mode
     */
    public XmlUtil(int userId) {
        this.userId = userId;
        this.mName2Task = new HashMap<>();
        this.daxPath = Parameters.getDaxPath();
        this.daxPaths = Parameters.getDAXPaths();
        this.jobIdStartsFrom = 1;
        this.hostList = new ArrayList<>();
        this.containerList = new ArrayList<>();
        setTaskList(new ArrayList<>());
    }

    /**
     * Start to parse a workflow which is a xml file(s).
     */
    public void parse() {
        if (this.daxPath != null) {
            parseXmlFile(new File(this.daxPath));
        } else if (this.daxPaths != null) {
            for (String path : this.daxPaths) {
                parseXmlFile(new File(path));
            }
        }
    }

    public void parseVMs(File f) throws Exception{
        try {

            SAXBuilder builder = new SAXBuilder();
            //parse using builder to get DOM representation of the XML file
            Document dom = builder.build(f);
            Element root = dom.getRootElement();
            List<Element> list = root.getChildren();
            for (Element node : list) {
                //Log.printLine("node: " + node.getName().toLowerCase());
                switch (node.getName().toLowerCase()) {
                    case "application":
                        String aName = node.getAttributeValue("Name");
                        String aMemBss = node.getAttributeValue("MemoryBssSize");
                        String aMemeData = node.getAttributeValue("MemoryDataSize");
                        String aMemPersistBss = node.getAttributeValue("MemoryPersistentBssSize");
                        String aMemPersistData = node.getAttributeValue("MemoryPersistentDataSize");
                        String aMemText = node.getAttributeValue("MemoryTextSize");
                        String requiredMem = node.getAttributeValue("RequiredMemorySize");
                        String periodTime = node.getAttributeValue("Period");
                        String cpuRequest = node.getAttributeValue("CpuRequest");
                        // Log.printLine(requiredMem);
                        String upBandwidth = node.getAttributeValue("UpBandwidth");
                        String downBandwidth = node.getAttributeValue("DownBandwidth");
                        String computeTime = node.getAttributeValue("ComputeTime");
                        String ip = node.getAttributeValue("IpAddress");
                        Integer memBss = Integer.parseInt(aMemBss);
                        Integer memData = Integer.parseInt(aMemeData);
                        Integer memPersistBss = Integer.parseInt(aMemPersistBss);
                        Integer memPersistData = Integer.parseInt(aMemPersistData);
                        Integer memText = Integer.parseInt(aMemText);
                        Integer reqMem = Integer.parseInt(requiredMem);
                        Integer upB = Integer.parseInt(upBandwidth);
                        Integer downB = Integer.parseInt(downBandwidth);
                        Double computeT = Double.parseDouble(computeTime);
                        String hardware = node.getAttributeValue("Hardware");

                        long runtimeT = (long) (20000 * computeT);
                        if (runtimeT < 100) {
                            runtimeT = 100;
                        }
                       /* if(Constants.pause.containsKey(this.jobIdStartsFrom)) {
                            Double last = Constants.pause.get(this.jobIdStartsFrom).getValue();
                            computeT += last * 1000 * Parameters.getRuntimeScale();
                        }*/
                        int size = this.taskList.size();
                        int sizeId = size + 1;
                        runtimeT *= Parameters.getRuntimeScale();
                        List<Element> fileListT = node.getChildren();
                        for (Element file : fileListT) {
                            if (file.getName().equals("A653SamplingPort")) {
                                List<Pair<String, String>> ipAndSizes = new ArrayList<>();
                                List<Element> f1 = file.getChildren();
                                if (f1.isEmpty()) {
                                    continue;
                                }
                                List<Element> ports = f1.get(0).getChildren();
                                for (Element port : ports) {
                                    String messageSize = port.getAttributeValue("MessageSize");
                                    String ipS = port.getAttributeValue("IpAddress");
                                    ipAndSizes.add(new Pair<>(ipS, messageSize));
                                    Log.printLine("App " + aName + " --- " + messageSize + " --> " + ipS);
                                }
                                Constants.name2Ips.put(aName, ipAndSizes);
                            }
                        }
                        CondorVM taskT;
                        //In case of multiple workflow submission. Make sure the jobIdStartsFrom is consistent.
                        synchronized (this) {
                            if(cpuRequest == null || cpuRequest.replace(" ", "").equals("")) {
                                taskT = new CondorVM(this.jobIdStartsFrom, userId, 20000, 1000, reqMem, 0, 0, "Xen", new CloudletSchedulerTimeShared());
                            }else{
                                double mips = 20000;
                                Double cpus = Double.parseDouble(cpuRequest);
                                Integer cpuInt = cpus.intValue();
                                taskT = new CondorVM(this.jobIdStartsFrom, userId, 20000, cpuInt, reqMem, 0, 0, "Xen", new CloudletSchedulerTimeShared());
                            }
                            this.jobIdStartsFrom++;
                        }
                        if(hardware != null && !hardware.equals("")) {
                            for(Host h: Constants.hosts) {
                                if(h.getName().equals(hardware)) {
                                    taskT.setHost(h);
                                    break;
                                }
                            }
                        }
                        taskT.setIp(ip);
                        taskT.setName(aName);
                        Constants.ip2taskName.put(ip, aName);
                        Constants.id2Name.put(taskT.getId(), aName);
                        taskT.setPeriodTime(Double.parseDouble(periodTime));
                        taskT.setComputeTime(runtimeT);
                        this.containerList.add(taskT);
                        Log.printLine("Job " + taskT.getName() + " : || compute time: " + computeTime + " || period: " + taskT.getPeriodTime() +" || ram: " + taskT.getRam() + " ip: " + taskT.getIp() + " || period: " + node.getAttributeValue("Period"));
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void parseHostXml(File f) {
        parseXmlFile(f);
    }

    /**
     * Sets the depth of a task
     *
     * @param task the task
     * @param depth the depth
     */
    private void setDepth(Task task, int depth) {
        if (depth  > task.getDepth()) {
            task.setDepth(depth);
        }
        for (Task cTask : task.getChildList()) {
            setDepth(cTask, task.getDepth() + 1 );
        }
    }

    public void parseContainerInfo(File f) throws Exception{
        try {

            SAXBuilder builder = new SAXBuilder();
            //parse using builder to get DOM representation of the XML file
            Document dom = builder.build(f);
            Element root = dom.getRootElement();
            List<Element> list = root.getChildren();
            for (Element node : list) {
                //Log.printLine("node: " + node.getName().toLowerCase());
                switch (node.getName().toLowerCase()) {
                    case "container":
                        ContainerInfo container = new ContainerInfo();
                        container.metadata = new HashMap<>();
                        container.apiVersion = "v1";
                        container.kind = "pod";
                        container.spec = new HashMap<>();
                        List<Object> containers = new ArrayList<>();
                        Map<String, Object> oneContainer = new HashMap<>();
                        //containers.add(oneContainer);
                        Log.printLine("解析容器信息: ");
                        Log.printLine("=====================================================");
                        String name = node.getAttributeValue("AppName");
                        Log.printLine("name: " + name);
                        oneContainer.put("image", node.getAttributeValue("image"));
                        //container.image = node.getAttributeValue("image");
                        Log.printLine("image: " + node.getAttributeValue("image"));
                        if(node.getAttributeValue("WorkingDir") != null && !node.getAttributeValue("WorkingDir").replace(" ","").equals(""))
                            oneContainer.put("workingDir", node.getAttributeValue("WorkingDir"));
                        //container.workingDir = node.getAttributeValue("WorkingDir");
                        Log.printLine("working dir: " + node.getAttributeValue("WorkingDir"));
                        List<Element> cList = node.getChildren();
                        List<String> commands = new ArrayList<>();
                        List<String> args = new ArrayList<>();
                        Map<String, String> labels = new HashMap<>();
                        for(Element e : cList) {
                            switch (e.getName()) {
                                case "command" :
                                    String cC = e.getAttributeValue("Content");
                                    Log.printLine("command: " + cC);
                                    commands.add(cC);
                                    break;
                                case "args":
                                    String cA = e.getAttributeValue("Content");
                                    Log.printLine("args: " + cA);
                                    args.add(cA);
                                    break;
                                case "label":
                                    String key = e.getAttributeValue("Key");
                                    String value = e.getAttributeValue("val");
                                    Log.printLine("label: " + key + " : " + value);
                                    labels.put(key, value);
                                    break;
                            }
                        }
                        if(!commands.isEmpty()) {
                            oneContainer.put("command", commands);
                        }
                        if(!args.isEmpty()) {
                            oneContainer.put("args", args);
                        }
                        if(!labels.isEmpty()) {
                            container.metadata.put("labels", labels);
                        }
                        containers.add(oneContainer);
                        container.spec.put("containers", containers);
                        Log.printLine("=====================================================");
                        //Constants.name2Container.put(name, container);
                        if(Constants.containerInfoMap.get(name) != null) {
                            Log.printLine(name + "容器信息存在更早版本，将被当前输入版本覆盖");
                        }
                        Constants.containerInfoMap.put(name, container);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Parsing Exception");
            throw e;
        }
    }

    public void rewriteAppInfoXml(File file) throws Exception{
        try {
            SAXBuilder builder = new SAXBuilder();
            Document dom = builder.build(file);
            Element root = dom.getRootElement();
            List<Element> list = root.getChildren();
            for(Element app: list) {
                String name = app.getAttributeValue("Name");
                Integer hostId = Constants.schedulerResult.get(name);
                if(hostId == null) {
                    continue;
                }
                String hostName = "";
                for(Host h: Constants.hosts) {
                    if(h.getId() == hostId) {
                        hostName = h.getName();
                        break;
                    }
                }
                app.setAttribute("Hardware", hostName);
            }
            XMLOutputter xmlOutput = new XMLOutputter();
            Format f = Format.getRawFormat();
            f.setIndent("  "); // 文本缩进
            f.setTextMode(Format.TextMode.TRIM_FULL_WHITE);
            xmlOutput.setFormat(f);

            // 把xml文件输出到指定的位置
            xmlOutput.output(dom,
                    new FileOutputStream(new File(
                            System.getProperty("user.dir")+"\\OutputFiles\\jobResult\\AppResult.xml")));
        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Parse a DAX file with jdom
     */
    private void parseXmlFile(File fil) {

        try {

            SAXBuilder builder = new SAXBuilder();
            //parse using builder to get DOM representation of the XML file
            Document dom = builder.build(fil);
            Element root = dom.getRootElement();
            List<Element> list = root.getChildren();
            for (Element node : list) {
                //Log.printLine("node: " + node.getName().toLowerCase());
                switch (node.getName().toLowerCase()) {
                    case "utilization":
                        String utilization = node.getAttributeValue("up");
                        String typeS = node.getAttributeValue("type");
                        if(typeS.equals("cpu")) {
                            Constants.cpuUp = Double.parseDouble(utilization);
                        } else if(typeS.equals("memory")) {
                            Constants.ramUp = Double.parseDouble(utilization);
                        }
                        break;
                    case "node":
                        String name = node.getAttributeValue("name");
                        String memory = node.getAttributeValue("memory");
                        String storage = node.getAttributeValue("storage");
                        //Log.printLine(storage);
                        String cores = node.getAttributeValue("cores");
                        String mips = node.getAttributeValue("mips");
                        String bandwidth = node.getAttributeValue("bandwidth");
                        String network = node.getAttributeValue("network");
                        Integer memory_MB = Integer.parseInt(memory);
                        Long storage_MB = Long.parseLong(storage);
                        Integer pes_size = Integer.parseInt(cores);
                        Double pe_mips = Double.parseDouble(mips);
                        Long bandwidth_gbps = Long.parseLong(bandwidth);
                        List<Pe> pes = new ArrayList<>();
                        for(int i = 0; i < pes_size * 1000; i++) {
                            pes.add(new Pe(i,new PeProvisionerSimple(pe_mips)));
                        }

                        Host host = new Host(hostId,
                                new RamProvisionerSimple(memory_MB),
                                new BwProvisionerSimple(bandwidth_gbps), storage_MB , pes,
                                new VmSchedulerTimeShared(pes));
                                //new ContainerPodSchedulerTimeShared(peList),
                                //WFCConstants.HOST_POWER[2]);
                        host.setName(name);
                        host.setCloudletScheduler(new CloudletSchedulerTimeShared());
                        host.datacenterName = network;
                        this.hostList.add(host);
                        Log.printLine("new Host " + hostId + " with pe: " + pes_size + " ram: " + memory_MB + " mips: " + mips + " storage: " + storage + " bandwidth: " + bandwidth);
                        hostId ++;
                        //WFCConstants.hostMips.put(host.getId(), pe_mips);
                        break;
                    case "job":
                        long length = 0;
                        String nodeName = node.getAttributeValue("id");
                        String nodeType = node.getAttributeValue("name");
                        /**
                         * capture runtime. If not exist, by default the runtime
                         * is 0.1. Otherwise CloudSim would ignore this task.
                         * BUG/#11
                         */
                        double runtime;
                        if (node.getAttributeValue("runtime") != null) {
                            String nodeTime = node.getAttributeValue("runtime");
                            runtime = 1000 * Double.parseDouble(nodeTime);
                            if (runtime < 100) {
                                runtime = 100;
                            }
                            length = (long) runtime;
                        } else {
                            Log.printLine("Cannot find runtime for " + nodeName + ",set it to be 0");
                        }   //multiple the scale, by default it is 1.0
                        length *= Parameters.getRuntimeScale();
                        List<Element> fileList = node.getChildren();
                        List<FileItem> mFileList = new ArrayList<>();
                        for (Element file : fileList) {
                            if (file.getName().toLowerCase().equals("uses")) {
                                String fileName = file.getAttributeValue("name");//DAX version 3.3
                                if (fileName == null) {
                                    fileName = file.getAttributeValue("file");//DAX version 3.0
                                }
                                if (fileName == null) {
                                    Log.print("Error in parsing xml");
                                }

                                String inout = file.getAttributeValue("link");
                                double size = 0.0;

                                String fileSize = file.getAttributeValue("size");
                                if (fileSize != null) {
                                    size = Double.parseDouble(fileSize) /*/ 1024*/;
                                } else {
                                    Log.printLine("File Size not found for " + fileName);
                                }

                                /**
                                 * a bug of cloudsim, size 0 causes a problem. 1
                                 * is ok.
                                 */
                                if (size == 0) {
                                    size++;
                                }
                                /**
                                 * Sets the file type 1 is input 2 is output
                                 */
                                Parameters.FileType type = Parameters.FileType.NONE;
                                switch (inout) {
                                    case "input":
                                        type = Parameters.FileType.INPUT;
                                        break;
                                    case "output":
                                        type = Parameters.FileType.OUTPUT;
                                        break;
                                    default:
                                        Log.printLine("Parsing Error");
                                        break;
                                }
                                FileItem tFile;
                                /*
                                 * Already exists an input file (fcom.wfc.cloudsimet output file)
                                 */
                                if (size < 0) {
                                    /*
                                     * Assuming it is a parsing error
                                     */
                                    size = 0 - size;
                                    Log.printLine("Size is negative, I assume it is a parser error");
                                }
                                /*
                                 * Note that CloudSim use size as MB, in this case we use it as Byte
                                 */
                                if (type == Parameters.FileType.OUTPUT) {
                                    /**
                                     * It is good that CloudSim does tell
                                     * whether a size is zero
                                     */
                                    tFile = new FileItem(fileName, size);
                                } else if (ReplicaCatalog.containsFile(fileName)) {
                                    tFile = ReplicaCatalog.getFile(fileName);
                                } else {

                                    tFile = new FileItem(fileName, size);
                                    ReplicaCatalog.setFile(fileName, tFile);
                                }

                                tFile.setType(type);
                                mFileList.add(tFile);

                            }
                        }
                        Task task;
                        //In case of multiple workflow submission. Make sure the jobIdStartsFrom is consistent.
                        synchronized (this) {
                            task = new Task(this.jobIdStartsFrom, length);
                            this.jobIdStartsFrom++;
                        }
                        task.setType(nodeType);
                        task.setUserId(userId);
                        task.setRam(120000);
                        mName2Task.put(nodeName, task);
                        for (FileItem file : mFileList) {
                            task.addRequiredFile(file.getName());
                        }
                        task.setFileList(mFileList);
                        this.getTaskList().add(task);

                        /**
                         * Add dependencies info.
                         */
                        break;
                    case "child":
                        List<Element> pList = node.getChildren();
                        String childName = node.getAttributeValue("ref");
                        if (mName2Task.containsKey(childName)) {

                            Task childTask = (Task) mName2Task.get(childName);

                            for (Element parent : pList) {
                                String parentName = parent.getAttributeValue("ref");
                                if (mName2Task.containsKey(parentName)) {
                                    Task parentTask = (Task) mName2Task.get(parentName);
                                    parentTask.addChild(childTask);
                                    childTask.addParent(parentTask);
                                }
                            }
                        }
                        break;

                    case "faultgenerator":
                        String fType = node.getAttributeValue("type");
                        if(fType.equals("LogNormal")) this.distributionFamily = DistributionGenerator.DistributionFamily.LOGNORMAL;
                        if(fType.equals("Weibull")) this.distributionFamily = DistributionGenerator.DistributionFamily.WEIBULL;
                        if(fType.equals("Gamma")) this.distributionFamily = DistributionGenerator.DistributionFamily.GAMMA;
                        if(fType.equals("Normal")) this.distributionFamily = DistributionGenerator.DistributionFamily.NORMAL;
                        for(Element element: node.getChildren()) {
                            if(element.getName().equals("scale")) this.scale = Double.parseDouble(element.getText());
                            if(element.getName().equals("shape")) this.shape = Double.parseDouble(element.getText());
                        }
                        Log.printLine("Fault Inject: || type: " + fType + " || scale: " + this.scale + " || shape: " + shape);
                        break;

                    case "application":
                        String aName = node.getAttributeValue("Name");
                        /*String aMemBss = node.getAttributeValue("MemoryBssSize");
                        String aMemeData = node.getAttributeValue("MemoryDataSize");
                        String aMemPersistBss = node.getAttributeValue("MemoryPersistentBssSize");
                        String aMemPersistData = node.getAttributeValue("MemoryPersistentDataSize");
                        String aMemText = node.getAttributeValue("MemoryTextSize");*/
                        String requiredMem = node.getAttributeValue("RequiredMemorySize");
                        String periodTime = node.getAttributeValue("Period");
                        String hardware = node.getAttributeValue("Hardware");
                       // Log.printLine(requiredMem);
                        //String upBandwidth = node.getAttributeValue("UpBandwidth");
                        //String downBandwidth = node.getAttributeValue("DownBandwidth");
                        String computeTime = node.getAttributeValue("ComputeTime");
                        String ip = node.getAttributeValue("IpAddress");
                        /*Integer memBss = Integer.parseInt(aMemBss);
                        Integer memData = Integer.parseInt(aMemeData);
                        Integer memPersistBss = Integer.parseInt(aMemPersistBss);
                        Integer memPersistData = Integer.parseInt(aMemPersistData);
                        Integer memText = Integer.parseInt(aMemText);*/
                        Integer reqMem = Integer.parseInt(requiredMem);
                        /*Integer upB = Integer.parseInt(upBandwidth);
                        Integer downB = Integer.parseInt(downBandwidth);*/
                        Double computeT = Double.parseDouble(computeTime);
                        String cpuRequest = node.getAttributeValue("CpuRequest");

                        long runtimeT = (long) (Constants.averageMIPS * computeT);
                        if (runtimeT < 100) {
                            runtimeT = 100;
                        }
                       /* if(Constants.pause.containsKey(this.jobIdStartsFrom)) {
                            Double last = Constants.pause.get(this.jobIdStartsFrom).getValue();
                            computeT += last * 1000 * Parameters.getRuntimeScale();
                        }*/
                        int size = this.taskList.size();
                        int sizeId = size + 1;
                        runtimeT *= Parameters.getRuntimeScale();
                        List<Element> fileListT = node.getChildren();
                        List<FileItem> mFileListT = new ArrayList<>();
                        for (Element file : fileListT) {
                            if (file.getName().equals("A653SamplingPort")) {
                                List<Pair<String, String>> ipAndSizes = new ArrayList<>();
                                List<Element> f1 = file.getChildren();
                                if (f1.isEmpty()) {
                                    continue;
                                }
                                List<Element> ports = f1.get(0).getChildren();
                                for (Element port : ports) {
                                    String messageSize = port.getAttributeValue("MessageSize");
                                    String ipS = port.getAttributeValue("IpAddress");
                                    ipAndSizes.add(new Pair<>(ipS, messageSize));
                                    //Log.printLine("App " + aName + " --- " + messageSize + " --> " + ipS);
                                }
                                Constants.name2Ips.put(aName, ipAndSizes);
                            }
                        }
                        Task taskT;
                        //In case of multiple workflow submission. Make sure the jobIdStartsFrom is consistent.
                        synchronized (this) {
                            taskT = new Task(this.jobIdStartsFrom, runtimeT);
                            this.jobIdStartsFrom++;
                        }
                        if(cpuRequest != null && !cpuRequest.replace(" ","").equals("")) {
                            Double cpus = Double.parseDouble(cpuRequest);
                            taskT.setNumberOfPes(cpus.intValue());
                        }else{
                            taskT.setNumberOfPes(1000);
                        }
                        taskT.setType(ip);
                        taskT.setUserId(userId);
                        taskT.setRam(reqMem);
                        taskT.name = aName;
                        taskT.hardware = hardware;
                        Constants.ip2taskName.put(ip, aName);
                        mName2Task.put(aName, taskT);
                        Constants.id2Name.put(taskT.getCloudletId(), aName);
                        for (FileItem file : mFileListT) {
                            taskT.addRequiredFile(file.getName());
                        }
                        taskT.setFileList(mFileListT);
                        taskT.setPeriodTime(Double.parseDouble(periodTime));
                        this.getTaskList().add(taskT);
                        Log.printLine("Job " + taskT.name + " : || compute time: " + computeTime + " : || cpu request: " + taskT.getNumberOfPes() + " || period: " + taskT.getPeriodTime() +" || ram: " + taskT.getRam() + " ip: " + taskT.getType() + " ||");
                    case "":
                }
            }
            /**
             * If a task has no parent, then it is root task.
             */
            ArrayList roots = new ArrayList<>();
            for (Task task : mName2Task.values()) {
                task.setDepth(0);
                if (task.getParentList().isEmpty()) {
                    roots.add(task);
                }
            }

            /**
             * Add depth from top to bottom.
             */
            for (Iterator it = roots.iterator(); it.hasNext();) {
                Task task = (Task) it.next();
                setDepth(task, 1);
            }
            /**
             * Clean them so as to save memory. Parsing workflow may take much
             * memory
             */
            this.mName2Task.clear();

        } catch (JDOMException jde) {
            Log.printLine("JDOM Exception;Please make sure your dax file is valid");

        } catch (IOException ioe) {
            Log.printLine("IO Exception;Please make sure dax.path is correctly set in your config file");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Parsing Exception");
        }
    }
}
