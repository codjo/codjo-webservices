package net.codjo.webservices.generator;
import net.codjo.plugin.common.CommandLineArguments;
import jade.content.onto.Ontology;
import java.io.File;
import org.apache.log4j.Logger;
/**
 *
 */
public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class);


    public static void main(String[] args) throws Exception {
        CommandLineArguments arguments = new CommandLineArguments(args);

        String targetDirectoryName = arguments.getArgument("targetDirectory");
        String ontologyClassName = arguments.getArgument("ontologyClass");
        String packageName = arguments.getArgument("package");

        Ontology onto = (Ontology)Class.forName(ontologyClassName).getDeclaredMethod("getInstance")
              .invoke(null);

        File targetDirectory = new File(targetDirectoryName);
        File wsdlDirectory = new File(targetDirectory, "wsdl");
        File sourcesDirectory = new File(targetDirectory, "java");
        wsdlDirectory.mkdirs();
        sourcesDirectory.mkdirs();
        File wsdlFile = new File(wsdlDirectory, onto.getName() + ".wsdl");

        LOG.info("Generation du WSDL");
        OntologyToWsdl.createWsdlFromOntology(onto, wsdlFile);

        LOG.info("Generation des objets clients axis");

        try {
            new MyWSDL2Java().run(new String[]{"--NStoPkg", "urn:" + onto.getName() + "=" + packageName,
                                               "--output", sourcesDirectory.getAbsolutePath(),
                                               wsdlFile.getAbsolutePath()});
        }
        catch (SecurityException exception) {
            ;
        }

        new ClassListGenerator().generate(sourcesDirectory, targetDirectory, packageName);
    }
}
