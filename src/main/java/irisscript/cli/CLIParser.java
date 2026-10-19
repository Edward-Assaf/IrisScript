package irisscript.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class CLIParser {

    public static CLIArguments parse(String[] rawArguments) {
        if (rawArguments.length == 0) {
            return CLIArguments.success(CLICommands.HELP, new CompilerOptions(), List.of());
        }
        String baseCommand = rawArguments[0].toLowerCase();
        CLICommands command = switch (baseCommand) {
            case "build" -> CLICommands.BUILD;
            case "run" -> CLICommands.RUN;
            case "help" -> CLICommands.HELP;
            default -> CLICommands.UNKNOWN;
        };
        if (command == CLICommands.UNKNOWN) {
            return CLIArguments.error("Unknown command: " + rawArguments[0]);
        }
        if (command == CLICommands.HELP) {
            return CLIArguments.success(CLICommands.HELP, new CompilerOptions(), List.of());
        }
        CompilerOptions options = new CompilerOptions();
        List<String> sourceFilesPaths = new ArrayList<>();
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
                Path sourceFilePath = Paths.get(rawArguments[i]);
                if (!Files.exists(sourceFilePath)) {
                    return CLIArguments.error("Specified file doesn't exist: " + rawArguments[i]);
                }
                sourceFilesPaths.add(sourceFilePath.toAbsolutePath().normalize().toString());
                continue;
            }
            String lowerCaseRawArgument = rawArguments[i].toLowerCase();
            switch (lowerCaseRawArgument) {
                case "-o", "--output":
                    if (i + 1 == rawArguments.length) {
                        return CLIArguments.error("Output directory is missing");
                    }
                    Path outputDirectory = Paths.get(rawArguments[++i]);
                    try {
                        Files.createDirectories(outputDirectory);
                    } catch (IOException e) {
                        return CLIArguments.error("Failed to create the specified output directory: "
                                + rawArguments[i]);
                    }
                    options.setOutputDirectory(outputDirectory.toAbsolutePath().normalize().toString());
                    break;
                case "-nc", "--no-color":
                    options.setColor(false);
                    break;
                default:
                    return CLIArguments.error("Unknown option: " + rawArguments[i]);
            }
        }
        if (sourceFilesPaths.isEmpty()) {
            return CLIArguments.error("No source file was provided");
        }
        return CLIArguments.success(command, options, sourceFilesPaths);
    }

}