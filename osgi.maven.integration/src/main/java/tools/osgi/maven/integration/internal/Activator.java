package tools.osgi.maven.integration.internal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

   @Override
   public void start( BundleContext context ) throws Exception {
      try {
         System.out.println( "DEPLOYED" );
         
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error starting bundle: %s, Error: %s", context.getBundle().getSymbolicName(), exception.getMessage() ), exception );
      }
   }

   @Override
   public void stop( BundleContext context ) throws Exception {

   }

}