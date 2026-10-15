package irisscript.cli;

import java.util.ArrayList;
import java.util.List;

final class CompilerOptions {

    private String outputDirectory;

    private boolean useColor;

    private List<String> programArguments;

    public CompilerOptions(String outputDirectory, boolean useColor, List<String> programArguments) {
        this.outputDirectory = outputDirectory;
        this.useColor = useColor;
        this.programArguments = programArguments;
    }

    public CompilerOptions() {
        this("out", true, new ArrayList<String>());
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public boolean usesColor() {
        return useColor;
    }

    public List<String> getProgramArguments() {
        return programArguments;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setColor(boolean useColor) {
        this.useColor = useColor;
    }

    public void addProgramArgument(String programArgument) {
        this.programArguments.add(programArgument);
    }

}