package irisscript.cli;

public class Main {

    public static void main(String[] args) {
        CLIArguments arguments = CLIParser.parse(args);
        if (arguments.hasError()) {
            System.out.println(arguments.errorMessage());
            System.exit(1);
        }
    }

}