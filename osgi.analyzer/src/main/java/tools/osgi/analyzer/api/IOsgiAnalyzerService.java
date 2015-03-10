package tools.osgi.analyzer.api;

import java.io.Reader;
import java.util.Dictionary;
import java.util.List;

import org.osgi.framework.Bundle;

public interface IOsgiAnalyzerService {

   List<Bundle> findBundlesWithUseConflicts();

   List<UseConflict> findUseConflicts( Bundle bundle );

   /** @see IOsgiAnalyzerService#findUseConflicts(Dictionary) */
   List<UseConflict> findUseConflicts( Reader headers );

   /**
    * Calculates use conflicts in the current OSGi environment based on the
    * manifest headers passed in
    * @param headers Manifest headers to find potential use conflicts in the
    * current environment
    * @return All Use Conflicts found for the specified headers
    */
   List<UseConflict> findUseConflicts( Dictionary<String, String> headers );

   List<Bundle> findBundlesWithMissingOptionalImports();

   List<MissingImport> findMissingOptionalImports( Bundle bundle );

   List<Bundle> getBundleForClassName( String fqcn );

   Bundle getBundleForClass( Class<?> clazz );

   List<Bundle> getDependentBundles( Bundle bundle );

   void diagnose( Throwable exception );
}
