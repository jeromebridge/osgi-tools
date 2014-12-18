package tools.osgi.analyzer.api;

/** Defines the known reasons for optional imports not being resolved on a bundle */
public enum MissingOptionalImportReasonType {
   /** Not Applicable */
   Null("", false),
   /** Import could be resolved but a refresh is required */
   RefreshRequired("Refresh Required", true),
   /** Unknown why the import is not resolved */
   Unknown("Unknown", false),
   /** No bundles are available to resolve the import */
   Unavailable("Unavailable", false),
   /** Import could be resolved but would result in a use conflict */
   UseConflict("Use Conflict", true), ;

   private MissingOptionalImportReasonType( String display ) {
      this.display = display;
   }

   private MissingOptionalImportReasonType( String display, boolean possibleResolutionAvailable ) {
      this.display = display;
      this.possibleResolutionAvailable = possibleResolutionAvailable;
   }

   private String display;
   private boolean possibleResolutionAvailable = false;

   public String display() {
      return display;
   }

   public boolean isPossibleResolutionAvailable() {
      return possibleResolutionAvailable;
   }

   @Override
   public String toString() {
      return display();
   }
}
