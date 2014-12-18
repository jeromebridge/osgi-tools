package tools.osgi.analyzer.api;

/** Defines the known reasons for optional imports not being resolved on a bundle */
public enum MissingOptionalImportReasonType {
   /** Not Applicable */
   Null(""),
   /** Import could be resolved but a refresh is required */
   RefreshRequired("Refresh Required"),
   /** Unknown why the import is not resolved */
   Unknown("Unknown"),
   /** No bundles are available to resolve the import */
   Unavailable("Unavailable"),
   /** Import could be resolved but would result in a use conflict */
   UseConflict("Use Conflict"), ;

   private MissingOptionalImportReasonType( String display ) {
      this.display = display;
   }

   private String display;

   public String display() {
      return display;
   }

   @Override
   public String toString() {
      return display();
   }
}
