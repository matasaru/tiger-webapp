package tiger;

import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import tiger.servlet.DashboardServlet;
import tiger.servlet.Path;


/**
 * Started with code from <a href="https://github.com/jetty-project/embedded-jetty-jsp">embedded-jetty-jsp example</a>
 */
public class WebApp {

    private static final String WEBAPP = "/webapp/";

    public static class JspStarter extends AbstractLifeCycle implements ServletContextHandler.ServletContainerInitializerCaller {
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ServletContextHandler context;

        public JspStarter(ServletContextHandler context) {
            this.context = context;
            this.context.setAttribute("org.apache.tomcat.JarScanner", new StandardJarScanner());
        }

        @Override
        protected void doStart() throws Exception {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(context.getClassLoader());
            try {
                sci.onStartup(null, context.getServletContext());
                super.doStart();
            }
            finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Server server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        URL webappUrl = server.getClass().getResource(WEBAPP);
        if (webappUrl == null) {
            throw new FileNotFoundException("Unable to find " + WEBAPP);
        }
        String resourceBase = webappUrl.toURI().toASCIIString();

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setContextPath("/");
        servletContextHandler.setResourceBase(resourceBase);

        // JSP wants an URLClassLoader
        ClassLoader jspClassLoader = new URLClassLoader(new URL[0], server.getClass().getClassLoader());
        servletContextHandler.setClassLoader(jspClassLoader);

        // this will call JettyJasperInitializer on context startup
        servletContextHandler.addBean(new JspStarter(servletContextHandler));

        // Set up JSP Servlet - must be named "jsp" per spec
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        // see org.apache.jasper.EmbeddedServletOptions for all supported options
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.8");
        holderJsp.setInitParameter("compilerSourceVM", "1.8");
        holderJsp.setInitParameter("keepgenerated", "true");
        servletContextHandler.addServlet(holderJsp, "*.jsp");

        // register Servlets
        servletContextHandler.addServlet(DashboardServlet.class, Path.DASHBOARD);

        // Default Servlet (always last, always named "default")
        ServletHolder holderDefault = new ServletHolder("default", DefaultServlet.class);
        holderDefault.setInitParameter("resourceBase", resourceBase);
        holderDefault.setInitParameter("dirAllowed", "false");
        servletContextHandler.addServlet(holderDefault, "/");
        server.setHandler(servletContextHandler);

        server.start();

        // server will keep running until it receives an Interrupt Signal, or SIGINT (`kill -TERM {pid}` or Ctrl+C)
        server.join();
    }
}
