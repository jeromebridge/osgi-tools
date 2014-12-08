package tools.osgi.analyzer.api;

import java.util.List;

import org.osgi.framework.Bundle;

import com.springsource.util.osgi.manifest.ImportedPackage;

public interface IOsgiAnalyzerService {

   BundleDiagnosisResult diagnose( Bundle bundle );

   List<Bundle> findBundlesWithUseConflicts();

   List<UseConflict> findUseConflicts( Bundle bundle );

   List<Bundle> findBundlesWithMissingOptionalImports();

   List<ImportedPackage> findMissingOptionalImports( Bundle bundle );
}
