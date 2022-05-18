package tiger;

import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import tiger.servlet.DashboardServlet;
import tiger.servlet.Path;


/**
 * Started with code from <a href="https://github.com/jetty-project/embedded-jetty-jsp">embedded-jetty-jsp example</a>
 */
public class JettyConfigurator {

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

    public static void config(Server server) throws Exception {

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

        // make sure there's no DefaultServlet, so unhandled requests get to the ring handler
        servletContextHandler.getServletHandler().setEnsureDefaultServlet(false);
        Handler handler = servletContextHandler.getSessionHandler().getHandler();
        if (handler instanceof ServletHandler servletHandler) {
            servletHandler.setEnsureDefaultServlet(false);
        }

        // save ring handler the server comes with
        var ringHandler = server.getHandler();

        // set a new composite handler
        // note: ringHandler must come after, since ring sets request#handled to true most of the time
        server.setHandler(new HandlerList(servletContextHandler, ringHandler));
    }
}
