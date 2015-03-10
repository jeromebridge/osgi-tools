package tools.osgi.analyzer.api;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;

public class UsesConflict {
   private BundleManifest manifest;
   private BundleContext bundleContext;
   private ImportedPackage importedPackage;
   private UsesConflictType type = UsesConflictType.Null;
   private Bundle usesConflictBundle;
   private ExportedPackage usesConflictExportedPackage;
   private ImportedPackage usesConflictImportedPackage;

   public UsesConflict() {}

   public UsesConflict( BundleContext bundleContext, BundleManifest manifest, ImportedPackage importedPackage, Bundle usesConflictBundle, ExportedPackage usesConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setManifest( manifest );
      setImportedPackage( importedPackage );
      setUsesConflictBundle( usesConflictBundle );
      setUsesConflictExportedPackage( usesConflictExportedPackage );
      setType( UsesConflictType.Wiring );
   }

   public UsesConflict( BundleContext bundleContext, BundleManifest manifest, ImportedPackage importedPackage, Bundle usesConflictBundle, ImportedPackage usesConflictExportedPackage ) {
      this.bundleContext = bundleContext;
      setManifest( manifest );
      setImportedPackage( importedPackage );
      setUsesConflictBundle( usesConflictBundle );
      setUsesConflictImportedPackage( usesConflictExportedPackage );
      setType( UsesConflictType.Header );
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

   public UsesConflictResolutionSuggestion getSuggestion() {
      final UsesConflictResolutionSuggestion result = new UsesConflictResolutionSuggestion( UsesConflictResolutionSuggestionType.None );
      result.setTargetBundle( usesConflictBundle );
      if( UsesConflictType.Wiring.equals( type ) ) {
         if( doesUsesConflictBundleWiringResolveImport() ) {
            result.setType( UsesConflictResolutionSuggestionType.UninstallBundle );
            result.setTargetBundle( getBundleWireBundle() );
         }
      }
      return result;
   }

   public UsesConflictType getType() {
      return type;
   }

   public Bundle getUsesConflictBundle() {
      return usesConflictBundle;
   }

   public BundleWire getUsesConflictBundleWire() {
      BundleWire result = null;
      if( usesConflictBundle != null ) {
         result = BundleUtils.getBundleWire( bundleContext, usesConflictBundle, importedPackage.getPackageName() );
      }
      return result;
   }

   public Bundle getUsesConflictBundleWireBundle() {
      Bundle result = null;
      if( getUsesConflictBundleWire() != null ) {
         result = getUsesConflictBundleWire().getProviderWiring().getBundle();
      }
      return result;
   }

   public ExportedPackage getUsesConflictBundleWireExportdPackage() {
      ExportedPackage result = null;
      if( getUsesConflictBundleWireBundle() != null ) {
         result = BundleUtils.getExportedPackage( getUsesConflictBundleWireBundle(), importedPackage );
      }
      return result;
   }

   public ExportedPackage getUsesConflictExportedPackage() {
      return usesConflictExportedPackage;
   }

   public ImportedPackage getUsesConflictImportedPackage() {
      return usesConflictImportedPackage;
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

   public void setType( UsesConflictType type ) {
      this.type = type;
   }

   public void setUsesConflictBundle( Bundle usesConflictBundle ) {
      this.usesConflictBundle = usesConflictBundle;
   }

   public void setUsesConflictExportedPackage( ExportedPackage usesConflictExportedPackage ) {
      this.usesConflictExportedPackage = usesConflictExportedPackage;
   }

   public void setUsesConflictImportedPackage( ImportedPackage usesConflictImportedPackage ) {
      this.usesConflictImportedPackage = usesConflictImportedPackage;
   }

   @Override
   public String toString() {
      return String.format( "Use Conflict: %s(%s)", importedPackage.getPackageName(), type.name() );
   }

   private boolean doesUsesConflictBundleWiringResolveImport() {
      return getUsesConflictBundleWireExportdPackage() != null;
   }
}
