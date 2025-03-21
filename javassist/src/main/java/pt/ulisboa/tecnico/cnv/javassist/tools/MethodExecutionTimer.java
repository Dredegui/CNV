package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;

import javassist.CtBehavior;
import javassist.CtClass;

public class MethodExecutionTimer extends CodeDumper {

    public MethodExecutionTimer(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void writeToFile(String message) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/output.txt"), message.getBytes(), java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        // only do if it is raytracerHandler
        if (!(behavior.getLongName().contains("pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler.handle"))) {
            System.out.println("Skipping " + behavior.getLongName());
            return;
        }
        behavior.addLocalVariable("startTime", CtClass.longType);
        behavior.insertBefore("startTime = System.nanoTime();");
        StringBuilder builder = new StringBuilder();
        behavior.addLocalVariable("endTime", CtClass.longType);
        behavior.addLocalVariable("opTime", CtClass.longType);
        builder.append("endTime = System.nanoTime();");
        builder.append("opTime = endTime-startTime;");
        String command = String.format("\"[%s] %s method call completed in: \" + opTime + \" ns!\"", this.getClass().getSimpleName(), behavior.getLongName());
        // write to file
        String writeToFileCommand = String.format("%s.writeToFile(%s);", MethodExecutionTimer.class.getName() ,command);
        // writeToFile(writeToFileCommand);
        builder.append(writeToFileCommand);
        //builder.append(String.format("System.out.println(\"[%s] %s method call completed in: \" + opTime + \" ns!\");",
        //        this.getClass().getSimpleName(), behavior.getLongName()));
        behavior.insertAfter(builder.toString());
    }
}
