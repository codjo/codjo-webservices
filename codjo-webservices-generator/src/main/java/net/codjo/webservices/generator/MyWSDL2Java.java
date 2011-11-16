package net.codjo.webservices.generator;
import java.util.List;
import org.apache.axis.utils.CLArgsParser;
import org.apache.axis.utils.CLOption;
import org.apache.axis.utils.Messages;
import org.apache.axis.wsdl.WSDL2Java;
/**
 *
 */
// Copie de la classe org.apache.axis.wsdl.WSDL2Java sans les System.exit()
public class MyWSDL2Java extends WSDL2Java {

    @SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
    @Override
    protected void run(String[] args) {
        // Parse the arguments
        CLArgsParser argsParser = new CLArgsParser(args, options);

        // Print parser errors, if any
        if (null != argsParser.getErrorString()) {
            System.err.println(
                  Messages.getMessage("error01", argsParser.getErrorString()));
            printUsage();
        }

        // Get a list of parsed options
        List clOptions = argsParser.getArguments();
        int size = clOptions.size();

        try {

            // Parse the options and configure the emitter as appropriate.
            for (int i = 0; i < size; i++) {
                parseOption((CLOption)clOptions.get(i));
            }

            // validate argument combinations
            //
            validateOptions();
            parser.run(wsdlURI);
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }    // run
}
