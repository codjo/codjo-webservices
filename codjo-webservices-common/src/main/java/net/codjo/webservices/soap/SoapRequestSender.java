package net.codjo.webservices.soap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import com.tilab.wsig.soap.SoapClient;
/**
 *
 */
public class SoapRequestSender {
    private SoapRequestSender() {
    }


    public static void main(String[] args) throws FileNotFoundException {
        if (args == null || args.length < 3) {
            System.out.println("usage: SoapRequestSender URL REQUEST_FILE RESPONSE_FILE");
            System.out.println("where:");
            System.out.println("URL: soap web-server url");
            System.out.println("REQUEST_FILE: xml file with soap message request");
            System.out.println("RESPONSE_FILE: file in which the response will be written");
            return;
        }

        String soapUrl = args[0];
        String requestFilename = args[1];
        String responseFilename = args[2];

        String response = SoapClient.sendFileMessage(soapUrl, requestFilename);

        File responseFile = new File(responseFilename);
        responseFile.delete();
        File parentDirectory = responseFile.getParentFile();
        if (parentDirectory != null) {
            parentDirectory.mkdirs();
        }

        PrintWriter writer = new PrintWriter(responseFilename);
        writer.write(response);
        writer.flush();
        writer.close();
    }
}
