package tools.osgi.analyzer.internal;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import tools.osgi.analyzer.api.OsgiAnalyzerService;

public class Activator implements BundleActivator {

   @Override
   public void start( BundleContext context ) throws Exception {
      try {
         final Hashtable<String, Object> props = new Hashtable<String, Object>();
         props.put( "osgi.command.scope", "tools" );
         props.put( "osgi.command.function", new String[]{ "analyze" } );
         context.registerService( OsgiAnalyzerService.class.getName(), new OsgiAnalyzerService( context ), props );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error starting bundle: %s, Error: %s", context.getBundle().getSymbolicName(), exception.getMessage() ), exception );
      }
   }

   @Override
   public void stop( BundleContext context ) throws Exception {

   }

}
