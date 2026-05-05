import javax.tools.*;
import java.net.URI;
import java.util.Collections;

public class scratch {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("No JavaCompiler available. Need JDK, not JRE.");
            return;
        }
        System.out.println("JavaCompiler available!");
    }
}
