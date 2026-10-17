package irisscript.cli;

import java.util.List;

final record CLIArguments(CLICommands command, CompilerOptions options, List<String> sourceFilePaths,
        String errorMessage) {

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public static CLIArguments success(CLICommands command, CompilerOptions options, List<String> sourceFilePaths) {
        return new CLIArguments(command, options, sourceFilePaths, null);
    }

    public static CLIArguments error(String errorMessage) {
        return new CLIArguments(CLICommands.UNKNOWN, new CompilerOptions(), List.of(), errorMessage);
    }

}