package tools.osgi.analyzer.api;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;

public class UseConflict {
   private BundleManifest manifest;
   private BundleContext bundleContext;
   private ImportedPackage importedPackage;
   private UseConflictType type = UseConflictType.Null;
   private Bundle useConflictBundle;
   private ExportedPackage useConflictExportedPackage;
   private ImportedPackage useConflictImportedPackage;

   public UseConflict() {}

   public UseConflict( BundleContext bundleContext, BundleManifest manifest, ImportedPackage importedPackage, Bundle useConflictBundle, ExportedPackage useConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setManifest( manifest );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictExportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Wiring );
   }

   public UseConflict( BundleContext bundleContext, BundleManifest manifest, ImportedPackage importedPackage, Bundle useConflictBundle, ImportedPackage useConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setManifest( manifest );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictImportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Header );
   }

   public Bundle getBundle() {
      return BundleUtils.getBundleByNameOrId( bundleContext, getManifest().getBundleSymbolicName().getSymbolicName() );
   }

   public BundleWire getBundleWire() {
      if( getBundle() == null ) {
         throw new RuntimeException( "No bundle can be found for the Use Conflict definition" );
      }
      return BundleUtils.getBundleWire( bundleContext, getBundle(), importedPackage.getPackageName() );
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

   public BundleManifest getManifest() {
      return manifest;
   }

   public void setManifest( BundleManifest manifest ) {
      this.manifest = manifest;
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
