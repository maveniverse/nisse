import java.util.Properties;

// classic syntax: ITs may run on any JDK the build supports
public class Nisse {
    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        cfg.load(System.in);
        System.out.println("greeting=hello-maven-" + cfg.getProperty("maven.version"));
        System.out.println("root=" + args[1]);
    }
}
