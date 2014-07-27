package tools.osgi.analyzer;

import org.apache.felix.service.command.Descriptor;
import org.osgi.framework.BundleContext;

public class OsgiAnalyzerService {

   BundleContext bundleContext;

   public OsgiAnalyzerService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   @Descriptor("Analyzes the state of the OSGi container")
   public void analyze( @Descriptor("the directory where the files are located") String directory ) {
      System.out.println( "Not Implemented Yet." );
   }

}
