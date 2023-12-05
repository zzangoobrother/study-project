package myjunit.mvc.part2;

import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class WebServerLauncher {
    public static void main(String[] args) throws Exception {
        String webappDirLocation = "my-framework/webapp/part2";
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);

        tomcat.addWebapp("/", new File(webappDirLocation).getAbsolutePath());

        tomcat.start();
        tomcat.getServer().await();
    }
}
