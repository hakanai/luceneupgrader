import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class RunScript {
    private final Path scriptFile;
    private final String version;

    public RunScript(Path scriptFile, String version) {
        this.scriptFile = scriptFile;
        this.version = version;
    }

    public void run() throws IOException, ScriptException {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try (Reader reader = Files.newBufferedReader(scriptFile)) {
            engine.put("arguments", new String[] { version });
            engine.eval(reader);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: java RunScript <script.js> <version>");
            System.exit(1);
        }

        Path scriptFile = Paths.get(args[0]);
        String version = args[1];

        try {
            new RunScript(scriptFile, version).run();
        } catch (IOException | ScriptException e) {
            System.err.println("Error running script");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
