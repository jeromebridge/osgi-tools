package tools.osgi.analyzer.api;

import java.io.Reader;
import java.util.Dictionary;
import java.util.List;

import org.osgi.framework.Bundle;

public interface IOsgiAnalyzerService {

   List<Bundle> findBundlesWithUsesConflicts();

   List<UsesConflict> findUsesConflicts( Bundle bundle );

   /** @see IOsgiAnalyzerService#findUsesConflicts(Dictionary) */
   List<UsesConflict> findUsesConflicts( Reader headers );

   /**
    * Calculates use conflicts in the current OSGi environment based on the
    * manifest headers passed in
    * @param headers Manifest headers to find potential use conflicts in the
    * current environment
    * @return All Use Conflicts found for the specified headers
    */
   List<UsesConflict> findUsesConflicts( Dictionary<String, String> headers );

   /** @see IOsgiAnalyzerService#findUsesConflicts(Dictionary, String) */
   List<UsesConflict> findUsesConflicts( Reader headers, String packageName );

   /**
    * Calculates use conflicts in the current OSGi environment based on the
    * manifest headers passed in
    * @param headers Manifest headers to find potential use conflicts in the
    * current environment
    * @param packageName Limit search for use conflicts to this package only
    * @return All Use Conflicts found for the specified headers
    */
   List<UsesConflict> findUsesConflicts( Dictionary<String, String> headers, String packageName );

   List<Bundle> findBundlesWithMissingOptionalImports();

   List<MissingImport> findMissingOptionalImports( Bundle bundle );

   List<Bundle> getBundleForClassName( String fqcn );

   Bundle getBundleForClass( Class<?> clazz );

   List<Bundle> getDependentBundles( Bundle bundle );

   void diagnose( Throwable exception );
}
