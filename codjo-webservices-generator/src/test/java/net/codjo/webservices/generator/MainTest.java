package net.codjo.webservices.generator;

import net.codjo.test.common.PathUtil;
import net.codjo.util.file.FileUtil;
import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import java.io.File;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MainTest {

    @Test
    public void test_generate() throws Exception {
        File basedir = PathUtil.findTargetDirectory(getClass());
        File outputdir = new File(basedir, "test/generated-sources");
        outputdir.delete();

        Main.main(new String[]{"-targetDirectory", outputdir.getAbsolutePath(),
                               "-ontologyClass", MyOntology.class.getName(),
                               "-package", "net.codjo.webservices.test"});

        assertTrue(new File(outputdir, "wsdl").exists());

        File wsdlFile = new File(outputdir, "wsdl/myOntology.wsdl");
        assertTrue(wsdlFile.exists());
        assertTrue(FileUtil.loadContent(wsdlFile).contains("myOntology"));

        assertTrue(new File(outputdir, "java").exists());
        assertTrue(new File(outputdir, "java/net/codjo/webservices/test").exists());
        File javaFile = new File(outputdir, "java/net/codjo/webservices/test/MyOntology_ServiceLocator.java");
        assertTrue(javaFile.exists());
        assertTrue(FileUtil.loadContent(javaFile).contains("package net.codjo.webservices.test"));

        File listFile = new File(outputdir, "resources/sourcesList.txt");
        assertTrue(listFile.exists());
        assertTrue(FileUtil.loadContent(listFile).contains("net.codjo.webservices.test.MyOntologySOAPStub"));
        assertTrue(FileUtil.loadContent(listFile).contains("net.codjo.webservices.test.MyOntology_PortType"));
        assertTrue(FileUtil.loadContent(listFile).contains("net.codjo.webservices.test.MyOntology_Service"));
        assertTrue(FileUtil.loadContent(listFile).contains(
              "net.codjo.webservices.test.MyOntology_ServiceLocator"));
    }


    public static class MyOntology extends Ontology {

        public MyOntology() {
            super("myOntology", BasicOntology.getInstance());
        }


        public static MyOntology getInstance() {
            return new MyOntology();
        }
    }
}
