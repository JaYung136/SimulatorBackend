package org.sim.service;



import org.apache.commons.math3.util.Pair;
import org.sim.cloudbus.cloudsim.Cloudlet;
import org.sim.cloudbus.cloudsim.Host;
import org.sim.cloudbus.cloudsim.Log;
import org.sim.workflowsim.Job;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class YamlWriter {
    /*public static com.wfc.cloudsim.workflowsim.k8s.Pod ParsePodFromPath(String path) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream input = new FileInputStream(new File(path));
        Scanner s = new Scanner(input).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        Log.printLine(result);
        Pod pod;
        pod = yaml.loadAs(input, Pod.class);
        return pod;
    }

    public static void main(String[] args) throws FileNotFoundException {
        Pod pod = YamlUtil.ParsePodFromPath("config/pod/pod.yml");

    }*/

    public void writeYaml(String path, List<? extends Cloudlet> pods) throws Exception {
        Log.printLine("YamlWriter: write yaml");
        Map<String, Boolean> judge = new HashMap<>();
        Exception ret = null;
        for(int i = 0; i < pods.size(); i++) {
            try {
                if (((Job) pods.get(i)).getTaskList().size() < 1) {
                    continue;
                }
                if (((Job) pods.get(i)).getCloudletStatus() == Cloudlet.FAILED) {
                    continue;
                }
                //Log.printLine(((Job)pods.get(i)).getTaskList().get(0).name + " is in writer's hand");
                if (judge.get(((Job) pods.get(i)).getTaskList().get(0).name) != null) {
                    //Log.printLine(((Job)pods.get(i)).getTaskList().get(0).name + " is already write");
                    continue;
                }
                judge.put(((Job) pods.get(i)).getTaskList().get(0).name, true);
                String name = ((Job) pods.get(i)).getTaskList().get(0).name;
                ContainerInfo c = Constants.containerInfoMap.get(name);
                if(c == null) {
                    Log.printLine(name+"缺少容器信息");
                    ret = new Exception(name+"缺少容器信息");
                    continue;
                }
                Host host = null;
                Integer hostId = Constants.schedulerResult.get(((Job) pods.get(i)).getTaskList().get(0).name);
                if (hostId == null) {
                    continue;
                }
                //Log.printLine(hostId);
                for (Host h : Constants.hosts) {
                    if (h.getId() == hostId) {
                        host = h;
                        break;
                    }
                }
                assert host != null;
                String nodeName = host.getName();
                c.spec.put("nodeName", nodeName);
                c.metadata.put("name", name.replace("_", "").replace(" ", "").replace("-",""));
                String cName = "container" + name;
                ((List<Map<String, Object>>)(c.spec.get("containers"))).get(0).put("name", cName.replace("_","").replace(" ","").replace("-",""));
                if(c.metadata.get("namespace") == null) {
                    c.metadata.put("namespace", "default");
                }
                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

                Yaml yaml = new Yaml(options);
                String yamlString = yaml.dumpAs(c, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
                String pathFile = path + "\\pod" + pods.get(i).getCloudletId() + ".yml";
                FileWriter writer = new FileWriter(pathFile);
                writer.write(yamlString);
                writer.close();

                System.out.println(name + "的 YAML文件已生成");
            } catch (Exception e) {
                e.printStackTrace();
                ret = e;
                continue;
            }
        }
    }

}
