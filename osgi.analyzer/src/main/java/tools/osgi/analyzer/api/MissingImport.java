package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;

import com.springsource.util.osgi.manifest.ImportedPackage;

/** Defines imports that are not resolved along with the potential reason for the missing import */
public class MissingImport {
   private ImportedPackage importedPackage;
   private MissingOptionalImportReasonType reason = MissingOptionalImportReasonType.Null;
   private List<UsesConflict> usesConflicts = new ArrayList<UsesConflict>();
   private Bundle match;

   public Bundle getMatch() {
      return match;
   }

   public void setMatch( Bundle match ) {
      this.match = match;
   }

   public List<UsesConflict> getUsesConflicts() {
      return usesConflicts;
   }

   public void setUsesConflicts( List<UsesConflict> usesConflicts ) {
      this.usesConflicts = usesConflicts;
   }

   public MissingOptionalImportReasonType getReason() {
      return reason;
   }

   public void setReason( MissingOptionalImportReasonType reason ) {
      this.reason = reason;
   }

   public MissingImport( ImportedPackage importedPackage ) {
      setImportedPackage( importedPackage );
   }

   public ImportedPackage getImportedPackage() {
      return importedPackage;
   }

   public void setImportedPackage( ImportedPackage importedPackage ) {
      this.importedPackage = importedPackage;
   }
}
