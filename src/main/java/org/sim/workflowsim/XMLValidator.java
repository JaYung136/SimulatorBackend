package org.sim.workflowsim;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;

public class XMLValidator {
    public static void main(String[] args) {
        try {
            // 创建Schema工厂
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // 加载Schema文件
            File schemaFile = new File("D:/Simulate/src/main/resources/templates/Host.xsd");
            Schema schema = factory.newSchema(schemaFile);

            // 创建校验器
            Validator validator = schema.newValidator();

            // 加载待校验的XML文件
            File xmlFile = new File("D:/Simulate/src/main/resources/templates/Host_8.xml");
            StreamSource source = new StreamSource(xmlFile);

            // 进行校验
            validator.validate(source);

            System.out.println("校验成功");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("校验失败：" + e.getMessage());
        }
    }
}