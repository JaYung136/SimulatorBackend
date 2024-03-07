package org.sim.controller;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.commons.math3.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.Log;
import org.sim.service.Constants;
import org.sim.service.ContainerInfo;
import org.sim.service.YamlWriter;
import org.sim.workflowsim.Task;
import org.sim.workflowsim.XmlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.integration.IntegrationProperties;
import org.springframework.web.bind.annotation.*;
import org.sim.service.service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.URLConnection;
import java.util.*;


@RestController
@CrossOrigin
public class SimulateController {

    private service service;

    /**
     *
     * 对文件进行Schema检验
     *
     * @param schemaFile :schema文件，位于项目Schema文件夹下
     * @param targetFile :待检验文件
     *
     * @return 如果通过检验，返回Message.Code == CODE.SUC 否则 Message.Code == CODE.FAIL
     *
     * */
    private Message schemaValid(File schemaFile, File targetFile) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(schemaFile);
            Validator validator = schema.newValidator();
            StreamSource source = new StreamSource(targetFile);
            validator.validate(source);
            System.out.println("校验成功");
            return Message.Success(null);
        } catch (Exception e) {
            return Message.Fail(e.getMessage());
        }
    }

    /**
     *
     * 将调度器要用到的全局变量重置
     *
     * */
    private void resetAllForScheduler() {
        Constants.results = new ArrayList<>();
        Constants.logs = new ArrayList<>();
        Constants.resultPods = new ArrayList<>();
        Constants.id2Name = new HashMap<>();
        Constants.taskList = new ArrayList<>();
        Constants.nodeEnough = true;
        Constants.schedulerResult = new HashMap<>();
        Constants.faultNum = new HashMap<>();
        Constants.records = new ArrayList<>();
        Constants.ip2taskName = new HashMap<>();
        Constants.name2Ips = new HashMap<>();
        Constants.app2Con = new HashMap<>();
        Constants.repeatTime = 1;
        Constants.finishTime = 0.0;
        service = new service();
    }

    /**
     *
     * 将仿真器要用到的全局变量重置
     *
     * */
    private void resetForSimulator() {
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

    /**
     *
     * 进行调度，调度结果<application.Name, host.Id> 存于Constants.schedulerResult中
     *
     * */
    public Message schedule(Integer a) {
        Message m = new Message();
        resetAllForScheduler();
        Constants.ifSimulate = false;
        try {
            service.simulate(a);
            if (!Constants.nodeEnough) {
                m.code = CODE.FAILED;
                m.message = "集群中节点资源不足";
                Log.printLine("节点资源不足");
                return m;
            }
            return Message.Success(null);
        } catch (Exception e) {
            return Message.Fail(e.getMessage());
        }
    }

    /**
     *
     * 进行仿真，repeatTime表示每个任务运行多少个周期
     *
     * */
    public Message simulate(Integer a, Integer repeatTime) {
        resetForSimulator();
        Constants.repeatTime  = repeatTime;
        Constants.ifSimulate = true;
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

    /**
     *
     * 写中间文件assign.json
     *
     * */
    private Message writeJson() {
        JSONArray array = new JSONArray();
        for(Result result: Constants.results) {
            JSONObject obj = new JSONObject().put("app", result.app).put("pausestart", result.pausestart).put("pauseend", result.pauseend).put("name", result.name).put("host", result.host).put("start", result.start).put("end", result.finish).put("size", result.size)
                    .put("mips", result.mips).put("pes", result.pes).put("type", result.type).put("datacenter", result.datacenter).put("ram", result.ram).put("containerperiod", result.period);
            array.put(obj);
        }
        try {
            String InputDir = System.getProperty("user.dir")+"\\Intermediate\\assign.json";
            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(InputDir), "UTF-8");

            osw.write(array.toString(4));

            osw.flush();//清空缓冲区，强制输出数据
            osw.close();//关闭输出流*/
        } catch (Exception e) {
            e.printStackTrace();
            return Message.Fail(e.getMessage());
        }
        return Message.Success(null);
    }

    /**
     *
     * 输出K8s可用的yml文件
     *
     * @return 只有种情况下返回Message.Success: 输入的ContainerInfo文件中包含了每一个application的容器信息
     *
     */
    private Message writeYaml() {
        YamlWriter writer = new YamlWriter();
        try {
            String path = System.getProperty("user.dir")+"\\OutputFiles\\yaml";
            File dir = new File(path);
            deleteDir(dir);
            dir.mkdirs();
            writer.writeYaml(path, Constants.resultPods);
            return Message.Success("generate successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return Message.Fail(e.getMessage());
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir
                        (new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        if(dir.delete()) {
            return true;
        } else {
            return false;
        }
    }


    @RequestMapping("/uploadhost")
    public Message uploadhost(MultipartFile file, HttpServletRequest req) throws IOException {
        System.out.println("上传host.xml文件");
        Message r = new Message();
        try {
            String InputDir = System.getProperty("user.dir")+"\\InputFiles";
            System.out.println(InputDir);
            File hostfile = new File(InputDir,"Input_Hosts.xml");
            boolean dr = hostfile.getParentFile().mkdirs(); //创建目录
            file.transferTo(hostfile);
            Message m = schemaValid(new File(System.getProperty("user.dir") + "\\Schema\\Host.xsd"), hostfile);
            if(m.code == CODE.FAILED) {
                return m;
            }
            Constants.hostFile = hostfile;
            XmlUtil util = new XmlUtil(1);
            util.parseHostXml(hostfile);
            Constants.hosts = util.getHostList();
        }catch (IOException e){
            r.message = e.getMessage();
            r.code = CODE.FAILED;
            System.out.print(e.getMessage());
        }
        r.message = "上传Host成功";
        r.code = CODE.SUCCESS;
        return r;
    }

    @RequestMapping("/uploadApp")
    public Message uploadApp(MultipartFile file, HttpServletRequest req) throws IOException {
        System.out.println("上传AppInfo.xml文件");
        Message r = new Message();
        try {
            String InputDir = System.getProperty("user.dir")+"\\InputFiles";
            System.out.println(InputDir);
            File appfile = new File(InputDir,"Input_AppInfo.xml");
            boolean dr = appfile.getParentFile().mkdirs(); //创建目录
            file.transferTo(appfile);
            Constants.appFile = appfile;
            XmlUtil util = new XmlUtil(1);
            util.parseHostXml(appfile);
        }catch (IOException e){
            r.message = e.getMessage();
            r.code = CODE.FAILED;
            System.out.print(e.getMessage());
        }
        r.message = "上传AppInfo成功";
        r.code = CODE.SUCCESS;
        return r;
    }

    @RequestMapping("/uploadContainer")
    public Message uploadContainer(MultipartFile file, HttpServletRequest req) throws IOException {
        System.out.println("上传ContainerInfo.xml文件");
        Message r = new Message();
        try {
            Constants.name2Container = new HashMap<>();
            XmlUtil xmlUtil = new XmlUtil(-1);
            String InputDir = System.getProperty("user.dir")+"\\InputFiles";
            System.out.println(InputDir);
            String originalFilename = file.getOriginalFilename();
            String fileType = originalFilename.substring(originalFilename.lastIndexOf("."));
            //Log.printLine(fileType);
            if(fileType.equals(".xml")) {
                File containerfile = new File(InputDir, "Input_Containers.xml");
                boolean dr = containerfile.getParentFile().mkdirs(); //创建目录
                file.transferTo(containerfile);
                Constants.containerFile = containerfile;
                xmlUtil.parseContainerInfo(containerfile);
            }else if(fileType.equals(".yml") || fileType.equals(".yaml")) {
                File containerfile = new File(InputDir, "Input_Containers.yml");
                boolean dr = containerfile.getParentFile().mkdirs(); //创建目录
                file.transferTo(containerfile);
                InputStream resource = new FileInputStream(InputDir + "\\Input_Containers.yml");
                if (Objects.nonNull(resource)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(resource);
                    ContainerInfo info = new ContainerInfo();
                    if(data.get("kind") == null) {
                        return Message.Fail("yaml文件缺少kind字段");
                    }else{
                        info.kind = (String) data.get("kind");
                    }
                    if(data.get("apiVersion") == null) {
                        return Message.Fail("yaml文件缺少apiVersion字段");
                    }else{
                        info.apiVersion = (String) data.get("apiVersion");
                    }
                    if(data.get("metadata") == null) {
                        return Message.Fail("yaml文件缺少metadata字段");
                    }else{
                        info.metadata = (Map<String, Object>) data.get("metadata");
                        if(info.metadata.get("name") == null) {
                            return Message.Fail("yaml文件的metadata字段中缺少name字段");
                        }
                    }
                    if(data.get("spec") == null) {
                        return Message.Fail("yaml文件缺少spec字段");
                    }else{
                        info.spec = (Map<String, Object>) data.get("spec");
                        if(info.spec.get("containers") == null || ((List<Map<String, Object>>)(info.spec.get("containers"))).isEmpty()) {
                            return Message.Fail("yaml文件的spec字段中缺少containers字段");
                        }
                        if(((List<Map<String, Object>>)(info.spec.get("containers"))).get(0).get("image") == null) {
                            return Message.Fail("yaml文件未指定容器镜像");
                        }
                    }
                    Log.printLine("解析容器信息: ");
                    Log.printLine("=====================================================");
                    Log.printLine("name:" + (String) info.metadata.get("name"));
                    Log.printLine("image:" + ((List<Map<String, Object>>)(info.spec.get("containers"))).get(0).get("image"));
                    for(Map.Entry<String, Object> i: ((List<Map<String, Object>>)(info.spec.get("containers"))).get(0).entrySet()) {
                        Log.printLine(i.getKey() + ": " + i.getValue());
                    }
                    Log.printLine("=====================================================");
                    if(Constants.containerInfoMap.get((String) info.metadata.get("name")) != null) {
                        Log.printLine((String) info.metadata.get("name") + "容器信息存在更早版本，将被当前输入版本覆盖");
                    }
                    Constants.containerInfoMap.put((String) info.metadata.get("name"), info);
                }
            }
        }catch (IOException e){
            r.message = e.getMessage();
            r.code = CODE.FAILED;
            System.out.print(e.getMessage());
        } catch (Exception e) {
            r.message = e.getMessage();
            r.code = CODE.FAILED;
            e.printStackTrace();
        }
        r.message = "上传ContainerInfo成功";
        r.code = CODE.SUCCESS;
        return r;
    }

    @RequestMapping("/uploadFault")
    public Message uploadFault(MultipartFile file, HttpServletRequest req) throws IOException {
        System.out.println("上传FaultInject.xml文件");
        Message r = new Message();
        try {
            String InputDir = System.getProperty("user.dir")+"\\InputFiles";
            System.out.println(InputDir);
            File faultfile = new File(InputDir,"Input_Fault.xml");
            boolean dr = faultfile.getParentFile().mkdirs(); //创建目录
            file.transferTo(faultfile);
            Message m = schemaValid(new File(System.getProperty("user.dir") + "\\Schema\\FaultInject.xsd"), faultfile);
            if(m.code == CODE.FAILED) {
                return m;
            }
            Constants.faultFile = faultfile;
            XmlUtil util = new XmlUtil(1);
            util.parseHostXml(faultfile);
        }catch (IOException e){
            r.message = e.getMessage();
            r.code = CODE.FAILED;
            System.out.print(e.getMessage());
        }
        r.message = "上传FaultInject失败";
        r.code = CODE.SUCCESS;
        return r;
    }



    @RequestMapping(value = "/startSimulate")
    public Message startScheduleAndSimulate(@RequestBody Map<String, Integer> req) {
        try{
            Message m = new Message();
            if(Constants.hostFile == null)  {
                return Message.Fail("host输入文件不存在");
            }
            if(Constants.appFile == null)  {
                return Message.Fail("appInfo输入文件不存在");
            }
            Integer arithmetic = req.get("arithmetic");
            Log.printLine("============================== 开始调度 ==============================");
            m = schedule(arithmetic);
            if(m.code == CODE.FAILED) {
                return m;
            }
            Log.printLine("============================== 开始仿真 ==============================");
            m = simulate(arithmetic, 3);
            if(m.code == CODE.FAILED) {
                return m;
            }
            Log.printLine("============================== 开始输出中间文件 ==============================");
            m = writeJson();
            if(m.code == CODE.FAILED) {
                return m;
            }
            Log.printLine("============================== 开始输出YAML文件 ==============================");
            m = writeYaml();
            return m;
        }catch (Exception e) {
            return Message.Fail(e.getMessage());
        }
    }

    @RequestMapping("/staticSimulate")
    public ResultDTO staticSimulate() {
        try {
            Constants.staticApp2Host = new HashMap<>();
            String midPath = System.getProperty("user.dir") + "\\Intermediate\\assign.json";
            File file = new File(midPath);
            FileReader fileReader = new FileReader(file);
            Reader reader = new InputStreamReader(new FileInputStream(file), "Utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            String jsonStr = sb.toString();
            com.alibaba.fastjson.JSONArray objects = com.alibaba.fastjson.JSON.parseArray(jsonStr);
            int size = objects.size();
            Log.printLine("更新后的调度结果：");
            List<Pair<Double, Double>> hosts = new ArrayList<>();
            for(Host h: Constants.hosts) {
                hosts.add(new Pair<Double, Double>(Double.valueOf(h.getNumberOfPes()), Double.valueOf(h.getRamProvisioner().getRam())));
            }
            for(int i = 0; i < size; i++){
                com.alibaba.fastjson.JSONObject object = objects.getJSONObject(i);
                Result r  = object.toJavaObject(Result.class);
                Constants.staticApp2Host.put(r.app, r.host);
                Log.printLine("任务" + r.app + " ===> 节点" + r.host);
                Integer hostId = -1;
                for(Host h: Constants.hosts) {
                    if(h.getName().equals(r.host)) {
                        hostId = h.getId();
                        break;
                    }
                }
                if(hostId == -1) {
                    Log.printLine(r.host + "不存在");
                    return ResultDTO.success(r.host + "不存在");
                }
                Double pes = hosts.get(hostId).getKey();
                Double ram = hosts.get(hostId).getValue();
                Double totalRetPe = Constants.hosts.get(hostId).getNumberOfPes() * (1 - Constants.cpuUp);
                Double totalRetRam = Constants.hosts.get(hostId).getRamProvisioner().getRam() * (1 - Constants.ramUp);
                pes -= r.pes * 1000;
                ram -= r.ram;
                if(pes <= totalRetPe || ram <= totalRetRam) {
                    Log.printLine("物理机" + r.host + "超载");
                    return ResultDTO.error("物理机" + r.host + "超载");
                }
                hosts.add(hostId, new Pair<>(pes, ram));
                Constants.schedulerResult.put(r.app, hostId);
            }
            Constants.results = new ArrayList<>();
            Constants.logs = new ArrayList<>();
            Constants.resultPods = new ArrayList<>();
            Constants.id2Name = new HashMap<>();
            Constants.nodeEnough = true;
            Constants.faultNum = new HashMap<>();
            Constants.records = new ArrayList<>();
            Constants.apps = new ArrayList<>();
            Constants.ip2taskName = new HashMap<>();
            Constants.name2Ips = new HashMap<>();
            simulate(8, 3);
            YamlWriter writer = new YamlWriter();
            try {
                String path = System.getProperty("user.dir")+"\\OutputFiles\\yaml";
                File dir = new File(path);
                writer.writeYaml(path, Constants.resultPods);
            } catch (Exception e) {
                return ResultDTO.error(e.getMessage());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return ResultDTO.success("generate successfully");
    }

    @RequestMapping("/pauseContainer")
    public ResultDTO pauseContainer(@RequestBody String req) {
        //Log.printLine("pauseContainer");
        JSONObject content = new JSONObject(req);
        Integer containerId = content.getInt("id");
        double start = content.getDouble("start");
        double last = content.getDouble("last");
        Constants.pause.put(containerId, new Pair<>(start, last));
        Log.printLine("AppInfo中输入的第"+containerId+"个容器将在每次运行" + start + "s后暂停" + last + "s");
        return ResultDTO.success("container " + containerId + " will be paused");
    }

    @RequestMapping("/deletePause")
    public ResultDTO deletePause(@RequestBody String req) {
        Log.printLine("deletePause");
        JSONObject content = new JSONObject(req);
        Integer containerId = content.getInt("id");
        if(containerId == -1) {
            Constants.pause = new HashMap<>();
        } else {
            Constants.pause.remove(containerId);
        }
        return ResultDTO.success("delete pause");
    }
}
