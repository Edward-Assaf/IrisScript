package irisscript.cli;

final record CLIArguments(CLICommands command, CompilerOptions options, String sourceFilePath, 
    String errorMessage) {

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public static CLIArguments success(CLICommands command, CompilerOptions options, String sourceFilePath) {
        return new CLIArguments(command, options, sourceFilePath, null);
    }

    public static CLIArguments error(String errorMessage) {
        return new CLIArguments(CLICommands.UNKNOWN, new CompilerOptions(), null, errorMessage);
    }

}