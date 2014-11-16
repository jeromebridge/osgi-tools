package tools.osgi.maven.integration.internal;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import tools.osgi.maven.integration.api.OsgiMavenIntegrationService;

public class Activator implements BundleActivator {

   @Override
   public void start( BundleContext context ) throws Exception {
      try {
         final Hashtable<String, Object> props = new Hashtable<String, Object>();
         props.put( "osgi.command.scope", "m2e" );
         props.put( "osgi.command.function", new String[]{ "deploy" } );
         context.registerService( OsgiMavenIntegrationService.class.getName(), new OsgiMavenIntegrationService( context ), props );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error starting bundle: %s, Error: %s", context.getBundle().getSymbolicName(), exception.getMessage() ), exception );
      }
   }

   @Override
   public void stop( BundleContext context ) throws Exception {

   }

}