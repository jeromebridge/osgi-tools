package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;

import com.springsource.util.osgi.manifest.ImportedPackage;

/** Defines imports that are optional and not resolved along with the potential reason for the missing import */
public class MissingOptionalImport {
   private ImportedPackage importedPackage;
   private MissingOptionalImportReasonType reason = MissingOptionalImportReasonType.Null;
   private List<UseConflict> useConflicts = new ArrayList<UseConflict>();
   private Bundle match;

   public Bundle getMatch() {
      return match;
   }

   public void setMatch( Bundle match ) {
      this.match = match;
   }

   public List<UseConflict> getUseConflicts() {
      return useConflicts;
   }

   public void setUseConflicts( List<UseConflict> useConflicts ) {
      this.useConflicts = useConflicts;
   }

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
