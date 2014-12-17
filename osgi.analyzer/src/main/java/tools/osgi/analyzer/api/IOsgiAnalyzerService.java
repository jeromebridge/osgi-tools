package tools.osgi.analyzer.api;

import java.util.List;

import org.osgi.framework.Bundle;

public interface IOsgiAnalyzerService {

   List<Bundle> findBundlesWithUseConflicts();

   List<UseConflict> findUseConflicts( Bundle bundle );

   List<Bundle> findBundlesWithMissingOptionalImports();

   List<MissingOptionalImport> findMissingOptionalImports( Bundle bundle );

   List<Bundle> getBundleForClassName( String fqcn );

   Bundle getBundleForClass( Class<?> clazz );

   List<Bundle> getDependentBundles( Bundle bundle );

   void diagnose( Throwable exception );
}
