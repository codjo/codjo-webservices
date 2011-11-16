package net.codjo.webservices.generator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;

public class ClassListGenerator {

    public void generate(File sourcesDirectory, File targetDirectory, String packageName) throws IOException {
        File targetPackage = new File(sourcesDirectory, packageName.replace('.', '/'));
        File resourcesDirectory = new File(targetDirectory, "resources");
        if (!resourcesDirectory.exists()) {
            resourcesDirectory.mkdirs();
        }
        File sourcesListFile = new File(resourcesDirectory, "sourcesList.txt");
        BufferedWriter out = new BufferedWriter(new FileWriter(sourcesListFile, true));

        File[] files = targetPackage.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".java");
            }
        });
        for (File file : files) {
            String className = file.getAbsolutePath();
            className = className
                  .substring(targetDirectory.getAbsolutePath().length() + 1, className.length() - 5);
            out.write(className.replace(File.separator, "."));
            out.newLine();
        }
        out.close();
    }
}
