package tools.osgi.analyzer.api;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;

import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;

public class UseConflict {
   private Bundle bundle;
   private BundleContext bundleContext;
   private ImportedPackage importedPackage;
   private UseConflictType type = UseConflictType.Null;
   private Bundle useConflictBundle;
   private ExportedPackage useConflictExportedPackage;
   private ImportedPackage useConflictImportedPackage;

   public UseConflict() {}

   public UseConflict( BundleContext bundleContext, Bundle bundle, ImportedPackage importedPackage, Bundle useConflictBundle, ExportedPackage useConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setBundle( bundle );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictExportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Wiring );
   }

   public UseConflict( BundleContext bundleContext, Bundle bundle, ImportedPackage importedPackage, Bundle useConflictBundle, ImportedPackage useConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setBundle( bundle );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictImportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Header );
   }

   public Bundle getBundle() {
      return bundle;
   }

   public BundleWire getBundleWire() {
      return BundleUtils.getBundleWire( bundleContext, bundle, importedPackage.getPackageName() );
   }

   public Bundle getBundleWireBundle() {
      Bundle result = null;
      if( getBundleWire() != null ) {
         result = getBundleWire().getProviderWiring().getBundle();
      }
      return result;
   }

   public ImportedPackage getImportedPackage() {
      return importedPackage;
   }

   public UseConflictResolutionSuggestion getSuggestion() {
      final UseConflictResolutionSuggestion result = new UseConflictResolutionSuggestion( UseConflictResolutionSuggestionType.None );
      result.setTargetBundle( useConflictBundle );
      if( UseConflictType.Wiring.equals( type ) ) {
         if( doesUseConflictBundleWiringResolveImport() ) {
            result.setType( UseConflictResolutionSuggestionType.UninstallBundle );
            result.setTargetBundle( getBundleWireBundle() );
         }
      }
      return result;
   }

   public UseConflictType getType() {
      return type;
   }

   public Bundle getUseConflictBundle() {
      return useConflictBundle;
   }

   public BundleWire getUseConflictBundleWire() {
      BundleWire result = null;
      if( useConflictBundle != null ) {
         result = BundleUtils.getBundleWire( bundleContext, useConflictBundle, importedPackage.getPackageName() );
      }
      return result;
   }

   public Bundle getUseConflictBundleWireBundle() {
      Bundle result = null;
      if( getUseConflictBundleWire() != null ) {
         result = getUseConflictBundleWire().getProviderWiring().getBundle();
      }
      return result;
   }

   public ExportedPackage getUseConflictBundleWireExportdPackage() {
      ExportedPackage result = null;
      if( getUseConflictBundleWireBundle() != null ) {
         result = BundleUtils.getExportedPackage( getUseConflictBundleWireBundle(), importedPackage );
      }
      return result;
   }

   public ExportedPackage getUseConflictExportedPackage() {
      return useConflictExportedPackage;
   }

   public ImportedPackage getUseConflictImportedPackage() {
      return useConflictImportedPackage;
   }

   public void setBundle( Bundle bundle ) {
      this.bundle = bundle;
   }

   public void setImportedPackage( ImportedPackage importedPackage ) {
      this.importedPackage = importedPackage;
   }

   public void setType( UseConflictType type ) {
      this.type = type;
   }

   public void setUseConflictBundle( Bundle useConflictBundle ) {
      this.useConflictBundle = useConflictBundle;
   }

   public void setUseConflictExportedPackage( ExportedPackage useConflictExportedPackage ) {
      this.useConflictExportedPackage = useConflictExportedPackage;
   }

   public void setUseConflictImportedPackage( ImportedPackage useConflictImportedPackage ) {
      this.useConflictImportedPackage = useConflictImportedPackage;
   }

   @Override
   public String toString() {
      return String.format( "Use Conflict: %s(%s)", importedPackage.getPackageName(), type.name() );
   }

   private boolean doesUseConflictBundleWiringResolveImport() {
      return getUseConflictBundleWireExportdPackage() != null;
   }
}
