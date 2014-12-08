package tools.osgi.analyzer.api;

import org.osgi.framework.Bundle;

import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;

public class UseConflict {
   private Bundle bundle;
   private ImportedPackage importedPackage;
   private UseConflictType type = UseConflictType.Null;
   private Bundle useConflictBundle;
   private ExportedPackage useConflictExportedPackage;
   private ImportedPackage useConflictImportedPackage;

   public UseConflict() {}

   public UseConflict( Bundle bundle, ImportedPackage importedPackage, Bundle useConflictBundle, ExportedPackage useConflictExportedPackage ) {
      setBundle( bundle );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictExportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Wiring );
   }

   public UseConflict( Bundle bundle, ImportedPackage importedPackage, Bundle useConflictBundle, ImportedPackage useConflictExportedPackage ) {
      setBundle( bundle );
      setImportedPackage( importedPackage );
      setUseConflictBundle( useConflictBundle );
      setUseConflictImportedPackage( useConflictExportedPackage );
      setType( UseConflictType.Header );
   }

   public Bundle getBundle() {
      return bundle;
   }

   public ImportedPackage getImportedPackage() {
      return importedPackage;
   }

   public UseConflictType getType() {
      return type;
   }

   public Bundle getUseConflictBundle() {
      return useConflictBundle;
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
}
