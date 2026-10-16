package irisscript.cli;

final class CLIParser {

    public static CLIArguments parse(String[] rawArguments) {
        if (rawArguments.length == 0) {
            return CLIArguments.error("No arguments were provided");
        }
        String baseCommand = rawArguments[0].toLowerCase();
        CLICommands command = switch (baseCommand) {
            case "build" -> CLICommands.BUILD;
            case "run" -> CLICommands.RUN;
            default -> CLICommands.UNKNOWN;
        };
        if (command == CLICommands.UNKNOWN) {
            return CLIArguments.error("Unknown command: " + rawArguments[0]);
        }
        CompilerOptions options = new CompilerOptions();
        String sourceFilePath = null;
        boolean programArgumentsStarted = false;
        for (int i = 1; i < rawArguments.length; i++) {
            if (programArgumentsStarted) {
                options.addProgramArgument(rawArguments[i]);
                continue;
            }
            if (rawArguments[i].equals("--")) {
                programArgumentsStarted = true;
                continue;
            }
            if (rawArguments[i].endsWith(".iris")) {
                if (sourceFilePath != null) {
                    return CLIArguments.error("Multiple source files were provided");
                }
                sourceFilePath = rawArguments[i];
                continue;
            }
            String lowerCaseRawArgument = rawArguments[i].toLowerCase();
            switch (lowerCaseRawArgument) {
                case "-o", "--output":
                    if (i + 1 == rawArguments.length) {
                        return CLIArguments.error("Output directory is missing");
                    }
                    options.setOutputDirectory(rawArguments[++i]);
                    break;
                case "-nc", "--no-color":
                    options.setColor(false);
                    break;
                default:
                    return CLIArguments.error("Unknown option: " + rawArguments[i]);
            }
        }
        if (sourceFilePath == null) {
            return CLIArguments.error("No source file was provided");
        }
        return CLIArguments.success(command, options, sourceFilePath);
    }

}