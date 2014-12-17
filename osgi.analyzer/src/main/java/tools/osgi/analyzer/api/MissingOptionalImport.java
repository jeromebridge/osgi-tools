package tools.osgi.analyzer.api;

import com.springsource.util.osgi.manifest.ImportedPackage;

/** Defines imports that are optional and not resolved along with the potential reason for the missing import */
public class MissingOptionalImport {
   private ImportedPackage importedPackage;
   private MissingOptionalImportReasonType reason = MissingOptionalImportReasonType.Null;

   public MissingOptionalImportReasonType getReason() {
      return reason;
   }

   public void setReason( MissingOptionalImportReasonType reason ) {
      this.reason = reason;
   }

   public MissingOptionalImport( ImportedPackage importedPackage ) {
      setImportedPackage( importedPackage );
   }

   public ImportedPackage getImportedPackage() {
      return importedPackage;
   }

   public void setImportedPackage( ImportedPackage importedPackage ) {
      this.importedPackage = importedPackage;
   }
}
